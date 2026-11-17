## 1. Configuração

- [x] 1.1 `application.yml`: remover `logging.pattern.level`, adicionar `logging.structured.format.console: logstash`
- [x] 1.2 Garantir que `application-local.yml` não sobrescreve `logging.*` (mesmo formato JSON no local)
- [x] 1.3 Teste: subir app (default e `local`) e confirmar linha JSON com `requestId`, `userId`, `sub` como campos de topo

## 2. Actuator fora do log

- [x] 2.1 `RequestLoggingFilter`: não logar `Request start` para URI `/actuator/**`
- [x] 2.2 `RequestLoggingFilter`: não logar `Request end` para `/actuator/**` com status < 500; 5xx continua logando
- [x] 2.3 Teste do filter: `/actuator/health` 200 sem logs; 503 com log de erro; `/api/...` loga start/end

## 3. Refactor para fluent API (adapter)

- [x] 3.1 `RequestLoggingFilter`: `Request start/end/unhandled error` com mensagem constante e `method`, `path`, `status`, `durationMs`; `setCause(e)` no erro
- [x] 3.2 `ControllerLoggingAspect`: `Controller in/out` com `controller`, `args`, `result`, `durationMs` como key-values
- [x] 3.3 `GlobalExceptionHandler`: `Request rejected` com `status`, `code`, `reason`
- [x] 3.4 `SqsIngestionListener`: `Consumer in/out/failed`, warns, com `sourceId`, `durationMs`; remover `message={}` completo
- [x] 3.5 `HelloController`: log com `subject` (sem `claims` completos) via key-value

## 4. Refactor para fluent API (core)

- [x] 4.1 `IngestSourceUseCase`: `sourceId`, `s3Key`, `chunks` como key-values; `setCause` nos erros
- [x] 4.2 `SendMessageUseCase`: `conversationId`, `messageId`, `responseChars`, `sources` (número) como key-values
- [x] 4.3 `GenerateQuizUseCase`: `quizId`, `sources`, `chunks`, `attempt`, `reason`, `durationMs` como key-values
- [x] 4.4 Confirmar que `core` só importa `org.slf4j` (ArchUnit passa)

## 5. Verificação

- [x] 5.1 `rtk mvn test` verde
- [x] 5.2 Rodar local no perfil default, conferir que os eventos-chave saem como JSON com campos de topo e stack trace em `stack_trace`
- [x] 5.3 Atualizar `ARCHITECTURE.md` (seção de logs/observabilidade) com formato e exemplo de query Logs Insights
