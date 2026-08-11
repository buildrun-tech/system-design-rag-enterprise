## MODIFIED Requirements

### Requirement: Enviar mensagem e receber resposta via SSE
O sistema SHALL aceitar mensagens do usuário e retornar a resposta do LLM como stream de tokens via SSE (Server-Sent Events). Nesta fase, a resposta usa apenas o histórico de mensagens da conversa (sem similarity search em `source_chunks`); contexto RAG será restaurado em change futuro.

#### Scenario: Mensagem processada com sucesso
- **WHEN** um usuário envia `POST /conversations/{id}/messages` com `{"content": "..."}` pra uma conversa da qual é dono
- **THEN** o sistema persiste a mensagem do usuário com `role: user`
- **AND** monta o prompt com system message fixo + últimas 10 mensagens da conversa como histórico + a nova mensagem
- **AND** chama o LLM provider (streaming, via Spring AI `ChatClient`)
- **AND** retorna `200 OK` com `Content-Type: text/event-stream`
- **AND** cada token é enviado como `data: {"token": "..."}` via SSE
- **AND** ao finalizar, persiste a resposta completa com `role: assistant`, envia `data: {"done": true, "messageId": "..."}` e fecha o stream

#### Scenario: Conversa não existe ou pertence a outro usuário
- **WHEN** um usuário envia `POST /conversations/{id}/messages` pra um `conversationId` inexistente ou de outro usuário
- **THEN** o sistema retorna `404 Not Found` com `{"error": "CONVERSATION_NOT_FOUND"}` antes de iniciar qualquer chamada ao LLM

#### Scenario: Conteúdo da mensagem ausente ou vazio
- **WHEN** um usuário envia `POST /conversations/{id}/messages` sem `content` ou com `content` vazio/em branco
- **THEN** o sistema retorna `400 Bad Request` com `{"error": "VALIDATION_ERROR"}` antes de iniciar qualquer chamada ao LLM

#### Scenario: Falha durante o streaming do LLM
- **WHEN** o LLM provider falha ou expira durante o streaming (após o stream já ter iniciado)
- **THEN** o sistema envia `data: {"error": "STREAM_ERROR"}` e fecha o stream
- **AND** não persiste mensagem parcial do assistente
