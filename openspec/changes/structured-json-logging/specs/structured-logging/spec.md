## ADDED Requirements

### Requirement: Logs em JSON no formato logstash
O backend SHALL emitir cada evento de log em stdout como uma única linha JSON no formato `logstash` do Spring Boot, em todos os perfis, incluindo `local`.

#### Scenario: Qualquer perfil
- **WHEN** a aplicação roda com qualquer perfil, inclusive `local`
- **THEN** cada log é uma linha JSON com `@timestamp`, `message`, `logger_name` e `level`

#### Scenario: Exceção
- **WHEN** um log inclui uma exceção
- **THEN** o stack trace sai no campo `stack_trace` do mesmo evento JSON, sem quebra em múltiplos eventos

### Requirement: Contexto MDC como campos
O backend SHALL expor `requestId`, `userId` e `sub` presentes no MDC como campos de topo de cada evento JSON.

#### Scenario: Request autenticada
- **WHEN** uma request autenticada gera logs
- **THEN** todos os eventos dessa request contêm `requestId`, `userId` e `sub`

#### Scenario: Consumer SQS
- **WHEN** o `SqsIngestionListener` processa uma mensagem
- **THEN** os eventos contêm `requestId` e `userId` quando disponíveis

### Requirement: Dados de negócio como key-values
Logs que carregam dados (status, duração, identificadores) SHALL usar a fluent API do SLF4J com mensagem constante e `addKeyValue`, de modo que cada dado seja um campo JSON de topo e não parte do texto de `message`.

#### Scenario: Fim de request
- **WHEN** uma request HTTP termina
- **THEN** o evento tem `message` constante e campos `method`, `path`, `status` e `durationMs`

#### Scenario: Falha com exceção
- **WHEN** um log de erro registra uma exceção com identificadores
- **THEN** o evento contém os identificadores como campos e a causa em `stack_trace`

#### Scenario: Mensagem SQS
- **WHEN** o consumer registra recebimento de mensagem
- **THEN** o log contém `sourceId` como campo e NÃO contém o `toString` completo da mensagem

### Requirement: Actuator fora do log de requests
O backend SHALL NOT registrar `Request start` nem `Request end` de sucesso (status menor que 500) para URIs sob `/actuator`. Respostas 5xx sob `/actuator` SHALL ser logadas.

#### Scenario: Health check saudável
- **WHEN** o ALB chama `GET /actuator/health` e recebe 200
- **THEN** nenhum log de request é emitido

#### Scenario: Health check falhando
- **WHEN** `GET /actuator/health` responde 503
- **THEN** um log de erro `Request end` é emitido com `path` e `status`

#### Scenario: Request normal
- **WHEN** uma request a `/api/...` termina
- **THEN** logs de início e fim são emitidos normalmente
