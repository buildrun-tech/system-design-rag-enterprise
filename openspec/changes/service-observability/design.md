## Context

Amostra (`log-out.txt`, 252 eventos, 3m51s, local): `Request start/end` + `Controller in/out` = ~115 KB de 117 KB. `path` contém UUIDs. `Request end` do SSE mede só até o handler retornar. Nenhuma chamada externa é logada. `Controller in` loga `args` com texto da pergunta e nome de arquivo. Hibernate loga `duplicate key ... Key (email)=(...)` em WARN.

Infra: API Gateway HTTP, ALB, ECS service, RDS Postgres, SQS `ingest_queue` + DLQ (`infra/modules/sqs-queue`, visibility 180 s), buckets S3, Cognito, secret no Secrets Manager. Log group `aws_cloudwatch_log_group.this` em `modules/ecs-service`, logs em JSON. Amostra local: 0 erros, 20 req/min, ingestão de 111 s, SSE de 29 s.

## Goals / Non-Goals

**Goals:** métricas por rota sem alta cardinalidade; duração real do SSE; latência/erro por integração; menos volume e sem PII; dashboard e alarmes acionáveis provisionados em Terraform e aplicados na AWS.

**Non-Goals:** paralelizar o enrichment ou mudar `visibility_timeout` (só medir); backoff do polling no frontend; Micrometer/EMF; tracing (X-Ray/OTel); runbooks.

## Decisions

### Parte 1: logs

1. **`route`** vem do atributo `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` lido após `chain.doFilter` (filter já tem o `finally`). Sem match (404/OPTIONS) usa `"unmatched"`. `path` continua para debug.
2. **`service` e `env`** via `logging.structured.json.add.service` / `env` (Boot 4), valores de `${SERVICE_NAME:notebooklm-backend-api}` e `${APP_ENV:local}`. Sem código. `APP_ENV` entra no Terraform do ecs-service como env var.
3. **Remover `Request start` e `Controller out`.** `Request end` já tem duração e status; `Controller out` duplicava. `Controller in` fica só com `controller` e `args` filtrado: descartado `args` com conteúdo (texto de mensagem, nome de arquivo); mantém ids de path. Alternativa (mascarar) descartada: mais código, mesmo valor.
4. **SSE**: `SendMessageUseCase` guarda `start` e marca o primeiro token; `SSE stream out` e `failed` logam `ttfbMs`, `durationMs`, `outcome=ok|error`. `Request end` do SSE segue como está (documentado na spec que ele não mede o stream).
5. **Chamada externa**: helper pequeno `ExternalCallLog` no pacote `adapter/out` com `static <T> T measure(String integration, String operation, Supplier<T> call)` que loga `integration`, `operation`, `outcome=ok|error`, `durationMs` (e causa em erro) e relança. Nível INFO em sucesso, ERROR em falha. Sem interface, sem framework; uma classe. Alternativa AOP descartada: adapters usam clientes de libs com assinaturas variadas, wrapper explícito é mais legível.
6. **Enrichment**: `IngestSourceUseCase` loga `chunks` e `durationMs` do enrichment (`message="Source enrichment"`). Cada chamada ao modelo já sai via `ExternalCallLog` (integration=`openrouter`, operation=`enrich`).
7. **Hibernate**: `logging.level.org.hibernate.orm.jdbc.error: ERROR` em `application.yml`. Fix real do upsert fica em `fix-users-email-unique-constraint`.
8. **CORS**: `setMaxAge(3600)` no `CorsConfiguration` de `SecurityConfig`.

### Parte 2: dashboard e alarmes

1. **Módulo `observability` único**, recebendo nomes/ARNs (ALB, target group, cluster/service, RDS, filas, bucket, log group) por variável. Uma chamada em `main.tf`. Sem módulo por alarme.
2. **Fonte por widget:** métricas nativas quando existem; Logs Insights quando o dado só está no log (`route`, `integration`, `Consumer out`). Alternativa EMF/Micrometer descartada por ora: exige código e dependência; Insights + metric filters cobre o necessário.
3. **Layout** (4 linhas): visão geral (requests/min, % 5xx, p95, tasks up, DLQ); golden signals API (tráfego/latência/erros/saturação); integrações (OpenRouter, SQS, S3, RDS/pgvector, Cognito/Secrets); pipeline (ingestão, SSE, quiz).
4. **Latência por rota** via Insights `stats pct(durationMs,95) by route` filtrando `message="Request end" and method != "OPTIONS"`. Latência do ALB (`TargetResponseTime`) como visão independente do app.
5. **Metric filters** no log group: `ErrorCount` (`{ $.level = "ERROR" }`) e `IntegrationErrors` com dimensão por `integration` (`{ $.outcome = "error" }`). Alarmes a partir deles.
6. **Alarmes** (todos para SNS `alerts`): DLQ visível > 0; 5xx do target > 1% por 5 min; `ApproximateAgeOfOldestMessage` > 300 s; `HealthyHostCount` < `desired_count`; `ErrorCount` > 0 em 5 min; `IntegrationErrors` > 0 em 5 min. Sem alarme de latência no início (medir produção primeiro).
7. **Secrets Manager** sem widget próprio: falha aparece como task que não sobe (alarme `HealthyHostCount`) e erro no log; CloudTrail fica como investigação manual.
8. **S3 request metrics** habilitadas no bucket de sources via `aws_s3_bucket_metric` (filtro de bucket inteiro).
9. **E-mail de alerta** via variável `alert_email`; subscription precisa de confirmação manual.

## Risks / Trade-offs

- [`route` depende do atributo estar setado] → fallback `unmatched`; teste cobre.
- [Wrapper em todo adapter `out` aumenta toque no código] → só 5 pontos de I/O externo (OpenAI/OpenRouter, S3, SQS, pgvector); repositórios JPA ficam fora.
- [Log por chamada pode ser volumoso no enrichment (1 por chunk)] → INFO só com campos curtos; reavaliar nível se custo subir.
- [`maxAge` alto atrasa mudança de política CORS] → 1 h é suficiente.
- [Remover `Request start` perde marca de início em request que trava] → `Request end` não sai; alarme de latência/5xx no ALB cobre.

- [Insights custa por GB escaneado e dashboards recarregam] → janelas curtas por default, `limit`, e `observability-log-gaps` reduz ~97% do volume.
- [Widgets de OpenRouter/SSE vazios até o código novo ir pro ar] → dependência explícita; aplicar depois.
- [Alarme `ErrorCount` ruidoso (erros de cliente logados como ERROR)] → só `level=ERROR`; 4xx ficam em WARN.
- [Subscription SNS pendente de confirmação] → documentar no README de infra.
- [`terraform apply` altera conta AWS real] → confirmar conta/ambiente antes; `plan` revisado; só recursos novos.
- [Widgets de OpenRouter/SSE vazios até o código novo ir ao ar] → mesma change; deploy do backend antes de conferir os widgets.

## Migration Plan

1. Deploy do backend com os logs novos (`APP_ENV` no Terraform junto).
2. `terraform plan` e, após confirmação do alvo, `apply` do módulo `observability`.
3. Confirmar a subscription SNS por e-mail.
4. Rollback: reverter o backend; remover o módulo `observability`.

## Open Questions

- Paralelizar enrichment / revisar `visibility_timeout` (180 s) após ver dados reais de `Source enrichment`.
- Backoff do polling de `/sources` no frontend (29 chamadas em 106 s na amostra).
- Limiar de latência p95 para alarme, após dados de produção.
- Mais de um ambiente: dashboard por env ou um só com dimensão?
