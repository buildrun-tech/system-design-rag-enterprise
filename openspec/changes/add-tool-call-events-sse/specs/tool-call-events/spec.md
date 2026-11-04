## ADDED Requirements

### Requirement: Emitir eventos de ciclo de vida de tool call no stream SSE
O sistema SHALL emitir, no stream SSE de `POST /api/v1/conversations/{conversationId}/messages`, um evento `data: {"toolCall": {"id": "...", "name": "...", "status": "start" | "end" | "error", "durationMs": 0}}` para cada execução de tool feita pelo LLM, antes e depois da execução, intercalado com os eventos `token`.

#### Scenario: Tool executada com sucesso
- **WHEN** o LLM aciona uma tool durante a geração da resposta
- **THEN** o sistema envia `{"toolCall": {"id": "...", "name": "<tool>", "status": "start"}}` imediatamente antes de executar a tool
- **AND** envia `{"toolCall": {"id": "...", "name": "<tool>", "status": "end", "durationMs": <n>}}` após a execução, com o mesmo `id`
- **AND** a resposta continua com eventos `token` e termina com `done`

#### Scenario: Tool falha
- **WHEN** a execução da tool lança exceção
- **THEN** o sistema envia `{"toolCall": {"id": "...", "name": "<tool>", "status": "error", "durationMs": <n>}}`
- **AND** a exceção segue o tratamento normal do Spring AI, sem ser engolida

#### Scenario: Múltiplas tool calls
- **WHEN** o LLM aciona mais de uma tool na mesma resposta
- **THEN** cada execução recebe um `id` distinto e seus eventos `start` e `end`/`error` compartilham esse `id`

#### Scenario: Resposta sem tool call
- **WHEN** o LLM responde sem acionar tools
- **THEN** nenhum evento `toolCall` é enviado e o stream mantém o formato atual (`token`, `done`)

### Requirement: Eventos de tool call não expõem dados da tool
O sistema SHALL limitar o payload do evento `toolCall` a `id`, `name`, `status` e `durationMs`, sem enviar argumentos nem resultado da tool ao cliente.

#### Scenario: Payload mínimo
- **WHEN** uma tool é executada com argumentos e retorna um resultado
- **THEN** o evento `toolCall` não contém argumentos nem resultado

### Requirement: Eventos de tool call respeitam o ciclo de vida do stream
O sistema SHALL descartar eventos de tool call quando o stream já foi encerrado (cliente desconectou, timeout ou erro), sem lançar exceção para a thread da tool.

#### Scenario: Cliente desconecta durante a tool
- **WHEN** o cliente fecha a conexão enquanto uma tool executa
- **THEN** o evento `end` não é enviado e a execução da tool não é interrompida por erro de transporte

### Requirement: Tool call sem listener de stream
O sistema SHALL executar tools normalmente, sem emitir eventos, quando a chamada ao LLM não originou de um stream SSE de chat.

#### Scenario: Uso fora do chat
- **WHEN** o `ChatClient` é usado sem listener de eventos de tool no contexto
- **THEN** a tool executa e retorna o resultado normalmente
