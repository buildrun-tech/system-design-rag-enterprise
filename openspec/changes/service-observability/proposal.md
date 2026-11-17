## Why

Análise do `log-out.txt` mostrou que os logs JSON ainda não dão visão completa de saúde: sem `route` a latência por rota explode em cardinalidade; o SSE reporta 134 ms quando o stream levou 28,9 s; OpenRouter, S3, SQS e pgvector não têm log próprio; o enrichment (~107 s para 8 chunks) é invisível; 97% do volume é `Request start` + `Controller in/out`; há PII nos logs. Além disso não existe dashboard nem alarme no CloudWatch: a DLQ da ingestão não avisa ninguém e 5xx ou task fora do ar só aparecem para quem abre o console.

## What Changes

**Logs (backend)**
- `Request end` ganha `route`; todo evento ganha `service` e `env`.
- Remover `Request start` e `Controller out`; `Controller in` sem conteúdo do usuário.
- SSE: `ttfbMs`, `durationMs`, `outcome` em `SSE stream out`/`failed`.
- Log padronizado por chamada externa (`integration`, `operation`, `outcome`, `durationMs`) nos adapters `out`: OpenAI/OpenRouter, S3, SQS publish, pgvector.
- Log `Source enrichment` com `chunks` e `durationMs`.
- Hibernate `jdbc.error` em `ERROR` (sem e-mail em WARN); CORS `maxAge` de 3600 s.

**Dashboard e alarmes (Terraform, AWS)**
- Módulo `infra/modules/observability`: dashboard CloudWatch (golden signals, integrações, pipeline), metric filters sobre o JSON, alarmes (DLQ, 5xx, idade da fila, tasks saudáveis, ERROR, erro por integração) e tópico SNS com e-mail.
- Request metrics no bucket de sources.
- **Criar o dashboard e os alarmes na AWS** (`terraform apply`) como parte desta change, com confirmação do ambiente alvo antes.

Depende de `structured-json-logging` (formato logstash e fluent API).

## Capabilities

### New Capabilities
- `request-logging`: campos e volume dos logs de request/controller, sem PII, e duração/outcome do SSE.
- `integration-logging`: log padronizado de chamadas externas e do enrichment.
- `cloudwatch-monitoring`: dashboard de golden signals/integrações/pipeline e alarmes acionáveis.

### Modified Capabilities
<!-- nenhuma -->

## Impact

- Backend: `RequestLoggingFilter`, `ControllerLoggingAspect`, `SendMessageUseCase`, `IngestSourceUseCase`, adapters `out`, `SecurityConfig`, `application.yml`.
- `infra/`: novo módulo `observability`, `main.tf`, `ecs-service` (env `APP_ENV`), `s3-bucket` (request metrics), `README.md`.
- AWS: novos recursos (dashboard, alarmes, SNS, metric filters, S3 metrics); custo baixo, mais Logs Insights por GB escaneado.
- Queries Insights antigas baseadas em `Request start`/`Controller out` deixam de existir.
