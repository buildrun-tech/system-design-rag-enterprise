## ADDED Requirements

### Requirement: Campo route e identificação do serviço
O evento `Request end` SHALL conter o campo `route` com o template do endpoint (ex.: `/api/v1/notebooks/{id}`), ou `unmatched` quando nenhum handler casar. Todo evento de log SHALL conter `service` e `env`.

#### Scenario: Rota com id
- **WHEN** `GET /api/v1/notebooks/<uuid>` termina
- **THEN** `Request end` tem `route` igual a `/api/v1/notebooks/{id}` e `path` com o UUID

#### Scenario: Sem handler
- **WHEN** uma request sem handler correspondente termina
- **THEN** `Request end` tem `route` igual a `unmatched`

#### Scenario: Identificação
- **WHEN** qualquer evento é emitido
- **THEN** contém `service` e `env`

### Requirement: Volume e dados pessoais nos logs de request
O backend SHALL NOT emitir `Request start` nem `Controller out`. `Controller in` SHALL NOT incluir conteúdo enviado pelo usuário (texto de mensagem, nome de arquivo). Mensagens de erro de constraint do Hibernate com valores de colunas SHALL NOT aparecer em `WARN`.

#### Scenario: Request normal
- **WHEN** uma request a `/api/...` termina
- **THEN** existe exatamente um `Request end` e nenhum `Request start` ou `Controller out`

#### Scenario: Envio de mensagem
- **WHEN** o usuário envia uma mensagem no chat
- **THEN** nenhum log contém o texto da mensagem

#### Scenario: E-mail duplicado
- **WHEN** ocorre violação de `tb_users_email_key`
- **THEN** nenhum log em `WARN` ou inferior contém o e-mail

### Requirement: Duração e resultado do stream SSE
Os eventos `SSE stream out` e `SSE stream failed` SHALL conter `ttfbMs` (quando houve ao menos um token), `durationMs` total do stream e `outcome` (`ok` ou `error`).

#### Scenario: Stream concluído
- **WHEN** o stream termina com sucesso
- **THEN** `SSE stream out` tem `outcome=ok`, `ttfbMs` e `durationMs`

#### Scenario: Stream falha
- **WHEN** o stream falha no meio
- **THEN** `SSE stream failed` tem `outcome=error`, `durationMs` e `stack_trace`

### Requirement: Cache de preflight CORS
O backend SHALL responder preflight com `Access-Control-Max-Age` de 3600 segundos.

#### Scenario: Preflight
- **WHEN** o browser envia `OPTIONS` de origem permitida
- **THEN** a resposta contém `Access-Control-Max-Age: 3600`
