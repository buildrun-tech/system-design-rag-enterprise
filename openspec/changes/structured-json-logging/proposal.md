## Why

Logs do backend-api saem em texto puro (`logging.pattern.level`). No CloudWatch Logs Insights só existe `@message` crua; filtrar por `status`, `durationMs`, `requestId` exige `parse` com regex frágil. Stack trace multilinha vira vários eventos. Health check do ALB (`/actuator/health`) polui o log com `Request start/end` a cada poucos segundos.

## What Changes

- Ligar structured logging nativo do Spring Boot, formato `logstash` (JSON, 1 evento por linha), em todos os perfis, inclusive `local` (mesmo formato em dev e prod).
- MDC existente (`requestId`, `userId`, `sub`) passa a sair como campos de topo do JSON. Sem dependência nova.
- Refatorar logs com dados embutidos na mensagem (`status=`, `durationMs=`, `sourceId=`...) para SLF4J fluent API: mensagem constante + `addKeyValue(...)`, virando campos consultáveis.
- `SqsIngestionListener`: parar de logar `message={}` (toString inteiro do record); logar só campos úteis.
- `RequestLoggingFilter`: não logar `Request start/end` para `/actuator/**`. `requestId`/header `X-Request-Id` continuam normais.
- Sem mudança de Terraform: driver `awslogs` já aceita JSON e o Insights descobre os campos.

## Capabilities

### New Capabilities
- `structured-logging`: formato JSON (logstash) dos logs do backend, campos obrigatórios (MDC + key-values), convenção de mensagem constante + key-values, e exclusão de `/actuator/**` do log de requests.

### Modified Capabilities
<!-- nenhuma: não existe spec de logging anterior -->

## Impact

- `app/backend-api/src/main/resources/application.yml` e `application-local.yml` (config de logging).
- Código com `log.*`: `RequestLoggingFilter`, `ControllerLoggingAspect`, `GlobalExceptionHandler`, `SqsIngestionListener`, `IngestSourceUseCase`, `SendMessageUseCase`, `GenerateQuizUseCase`, `HelloController`.
- `core` continua sem Spring; usa apenas SLF4J (já usado hoje), então ArchUnit não é afetado.
- Queries existentes em CloudWatch baseadas em regex na `@message` deixam de funcionar (formato muda). Ver design.
- Tamanho do log sobe (JSON), impacto em custo/retention de CloudWatch.
