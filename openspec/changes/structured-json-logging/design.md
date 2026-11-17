## Context

`backend-api` (Spring Boot 4.1.0) loga texto via `logging.pattern.level` com MDC (`requestId`, `userId`, `sub`) em `RequestLoggingFilter`/`UserUpsertFilter`/`SqsIngestionListener`. Saída vai para stdout, driver `awslogs` (`infra/modules/ecs-service/main.tf`), CloudWatch Logs. Dados como `status`, `durationMs`, `sourceId` estão embutidos na string da mensagem. ALB bate em `/actuator/health` (`SecurityConfig`, `variables.tf`) e cada hit gera 2 logs.

## Goals / Non-Goals

**Goals:**
- Cada evento = 1 linha JSON, campos consultáveis no Logs Insights sem `parse`.
- Campos de negócio relevantes (`status`, `durationMs`, `sourceId`, `conversationId`, `quizId`...) como campos de topo.
- Sem ruído de `/actuator/**` nos logs de request.

**Non-Goals:**
- Tracing distribuído / OpenTelemetry.
- Mudar níveis de log, retention ou Terraform.
- Migrar queries/dashboards existentes (não há versionados no repo).
- Logar o frontend.

## Decisions

1. **`logging.structured.format.console: logstash`** (nativo do Boot) em vez de `ecs` ou Logback XML + `logstash-logback-encoder`. Sem dependência nova; campos simples (`level`, `logger_name`, `message`, `stack_trace`) sem `@`/ponto nas queries. ECS descartado: só compensa com Elastic. Encoder externo descartado: nativo cobre o caso.
2. **Mesmo formato em todos os perfis, incluindo `local`.** Config só em `application.yml`; `application-local.yml` não sobrescreve. `logging.pattern.level` é removido (ignorado em modo estruturado). Paridade dev/prod; custo: log local menos legível (usar `jq`).
3. **MDC como campos de topo**: comportamento default do formato logstash, sem config extra.
4. **Fluent API do SLF4J** (`log.atInfo().setMessage("...").addKeyValue(k, v).log()`) nos logs com dados. Mensagem constante (agrupável no Insights por `message`), dados em key-values. Key-values são incluídos pelo formato logstash como campos de topo. Alternativa descartada: `StructuredArguments` do logstash-encoder (dependência).
   - Exceções: `.setCause(e)` para manter `stack_trace`.
   - `core` usa só SLF4J (já usa), ArchUnit intacto.
5. **Convenção de nomes de campos** (camelCase, igual aos de hoje): `method`, `path`, `status`, `durationMs`, `sourceId`, `s3Key`, `chunks`, `conversationId`, `messageId`, `responseChars`, `sources`, `quizId`, `attempt`, `reason`, `code`, `controller` (nome `Classe.metodo`).
6. **Actuator fora do log de request**: `RequestLoggingFilter` pula os logs `Request start/end` quando `requestURI` começa com `/actuator`. MDC e header `X-Request-Id` continuam. Erro 5xx em `/actuator` ainda deve logar (health quebrado importa): pula só quando `status < 500`, e o `start` nunca loga para actuator.
7. **`ControllerLoggingAspect`**: `args`/`result` seguem como string truncada (`cut`) em campos `args`/`result`; `Controller in/out` com `controller` e `durationMs` como campos. Actuator não passa por ele (não é controller do pacote).
8. **`SqsIngestionListener`**: `Consumer in` loga `sourceId`/`requestId` em vez de `message={}` completo.

## Risks / Trade-offs

- [Queries com `parse @message` quebram] → Aviso no tasks; mensagens mantêm texto base, mas campos agora são nativos. Sem queries versionadas no repo.
- [Log maior (JSON), custo CloudWatch] → Aceito; `log_retention_days` já controla. Avaliar após deploy.
- [`args`/`result` do controller podem conter dado sensível] → Comportamento já existente, truncado em 1000; fora de escopo, só mantido.
- [Campo com mesmo nome e tipos diferentes (ex.: `sources` número vs lista) confunde Insights] → Um nome por tipo; padronizar na tabela acima.
- [Key-values em `core` acoplam a SLF4J 2] → Já é a API usada; sem custo extra.

## Migration Plan

1. Merge e deploy normal. Primeiros logs JSON aparecem no mesmo log group.
2. Eventos antigos (texto) e novos (JSON) coexistem até expirar retention; Insights trata ambos, campos só existem nos novos.
3. Rollback: reverter `application.yml` (remove `logging.structured`); o refactor fluent é inofensivo em formato texto, mas key-values não aparecem no pattern (só a mensagem constante).

## Open Questions

- Nenhuma bloqueante. Avaliar depois se vale adicionar `service.name` via `logging.structured.json.add` para multi-serviço.
