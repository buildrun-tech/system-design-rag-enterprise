## 1. Config e request logging

- [x] 1.1 `application.yml`: `logging.structured.json.add.service/env` e `logging.level.org.hibernate.orm.jdbc.error: ERROR`
- [x] 1.2 Terraform `ecs-service`: env var `APP_ENV` (e `SERVICE_NAME` se necessário)
- [x] 1.3 `RequestLoggingFilter`: adicionar `route` (fallback `unmatched`), remover `Request start`
- [x] 1.4 `ControllerLoggingAspect`: remover `Controller out`; `Controller in` sem conteúdo do usuário
- [x] 1.5 `SecurityConfig`: `setMaxAge(3600)` no CORS
- [x] 1.6 Testes: `route` (com id e `unmatched`), ausência de start/out, `Max-Age` no preflight, mensagem do chat ausente dos logs

## 2. SSE

- [x] 2.1 `SendMessageUseCase`: medir `ttfbMs` e `durationMs`; `outcome` em `SSE stream out` e `SSE stream failed`
- [x] 2.2 Teste do listener: sucesso e erro com campos esperados

## 3. Chamadas externas

- [x] 3.1 Criar `ExternalCallLog.measure(...)` em `adapter/out`
- [x] 3.2 Aplicar em `OpenAiChatCompletionAdapter`, `OpenAiQuizGenerationAdapter`, `SpringAiChunkEnrichmentAdapter`, `SpringAiDocumentEnrichmentAdapter` (`integration=openrouter`)
- [x] 3.3 Aplicar em `S3FileStorageAdapter`, `SqsIngestionQueueAdapter`, `PgVectorChunkStoreAdapter`, `PgVectorChunkInsightsAdapter`
- [x] 3.4 `IngestSourceUseCase`: log `Source enrichment` com `chunks` e `durationMs`
- [x] 3.5 Teste unitário do helper (ok, erro com relançamento) e ArchUnit verde

## 4. Verificação

- [x] 4.1 `rtk mvn test` verde
- [ ] 4.2 Rodar local, gerar novo `log-out.txt` e conferir campos (`route`, `service`, `env`, `integration`, SSE) e queda de volume
- [x] 4.3 Atualizar seção 6 de `ARCHITECTURE.md` com os campos novos

## 5. Módulo

- [x] 5.1 Criar `infra/modules/observability` (variáveis: ALB, target group, cluster/service, RDS, filas, bucket, log group, `desired_count`, `alert_email`)
- [x] 5.2 Chamar o módulo em `infra/main.tf` e expor `dashboard_name` em outputs
- [x] 5.3 `aws_s3_bucket_metric` no bucket de sources (módulo `s3-bucket`)

## 6. Dashboard

- [x] 6.1 Linha 1: requests/min, % 5xx, p95, tasks saudáveis, DLQ
- [x] 6.2 Linha 2: golden signals (ALB/API Gateway, Insights por `route`, ECS CPU/mem, RDS)
- [x] 6.3 Linha 3: integrações (SQS, S3, RDS, erros por `integration`, 401 do Cognito)
- [x] 6.4 Linha 4: pipeline (ingestão por `Consumer out`/`failed`, SSE, quiz)

## 7. Alarmes

- [x] 7.1 Tópico SNS e subscription por `alert_email`
- [x] 7.2 Metric filters `ErrorCount` e `IntegrationErrors` (dimensão `integration`)
- [x] 7.3 Alarmes: DLQ, 5xx, idade da fila, HealthyHostCount, ErrorCount, IntegrationErrors

## 8. Verificação

- [x] 8.1 `terraform fmt` e `terraform validate` sem erro
- [ ] 8.2 `terraform plan` mostra apenas recursos novos
- [ ] 8.3 Confirmar com o usuário conta/ambiente AWS alvo e rodar `terraform apply` (dashboard, alarmes, SNS, metric filters)
- [ ] 8.4 Após apply e deploy do backend, gerar tráfego e erro de teste; conferir widgets e disparo de um alarme
- [x] 8.5 Documentar no `infra/README.md` (confirmar subscription SNS) e na `ARCHITECTURE.md`
