## Purpose

Fornecer a interface de conversa com RAG dentro de um notebook, permitindo que o usuário selecione quais sources estão ativas por sessão, com histórico de conversa persistido e respostas do LLM streamadas via SSE (Server-Sent Events), para uma experiência de chat responsiva e rastreável.

## Requirements

### Requirement: Iniciar conversa em um notebook
O sistema SHALL permitir criar uma nova conversa dentro de um notebook, com seleção opcional de quais sources estão ativas para o RAG.

#### Scenario: Criação de conversa com sources selecionadas
- **WHEN** um usuário envia `POST /notebooks/{id}/conversations` com lista de `activeSourceIds`
- **THEN** o sistema cria uma conversa com as sources ativas registradas
- **AND** retorna `201 Created` com `{"conversationId": "...", "activeSourceIds": [...]}`

#### Scenario: Criação de conversa sem selecionar sources (todas ativas)
- **WHEN** um usuário envia `POST /notebooks/{id}/conversations` sem `activeSourceIds`
- **THEN** o sistema cria uma conversa com todas as sources READY do notebook ativas

### Requirement: Enviar mensagem e receber resposta via SSE
O sistema SHALL aceitar mensagens do usuário e retornar a resposta do LLM como stream de tokens via SSE (Server-Sent Events).

#### Scenario: Mensagem com RAG bem-sucedido
- **WHEN** um usuário envia `POST /conversations/{id}/messages` com `{"content": "..."}`
- **THEN** o sistema realiza similarity search no pgvector filtrado pelas sources ativas
- **AND** monta prompt com os chunks recuperados como contexto
- **AND** chama o LLM provider (streaming)
- **AND** retorna `200 OK` com `Content-Type: text/event-stream`
- **AND** cada token é enviado como `data: {"token": "..."}` via SSE
- **AND** ao finalizar, envia `data: {"done": true}` e fecha o stream

#### Scenario: Mensagem sem sources ativas ou READY
- **WHEN** um usuário envia mensagem em uma conversa sem sources ativas com status READY
- **THEN** o sistema responde normalmente via LLM sem contexto RAG
- **AND** inclui aviso no response que nenhuma source estava disponível

### Requirement: Histórico de conversa persistido
O sistema SHALL persistir todas as mensagens (usuário e assistente) no banco de dados, vinculadas à conversa.

#### Scenario: Persistência de mensagem do usuário
- **WHEN** o usuário envia uma mensagem
- **THEN** a mensagem é persistida em `conversation_messages` com `role: user`

#### Scenario: Persistência de resposta do assistente
- **WHEN** o LLM finaliza o streaming da resposta
- **THEN** a resposta completa é persistida em `conversation_messages` com `role: assistant`

#### Scenario: Recuperação do histórico para contexto
- **WHEN** o sistema monta o prompt para o LLM
- **THEN** as últimas N mensagens da conversa são incluídas no prompt como histórico

### Requirement: Listar conversas de um notebook
O sistema SHALL retornar todas as conversas de um notebook, ordenadas por `created_at` decrescente.

#### Scenario: Listagem de conversas
- **WHEN** um usuário envia `GET /notebooks/{id}/conversations`
- **THEN** o sistema retorna `200 OK` com lista de conversas com `id`, `created_at`, `preview` (primeira mensagem truncada)

### Requirement: Buscar mensagens de uma conversa
O sistema SHALL retornar as mensagens de uma conversa específica em ordem cronológica.

#### Scenario: Recuperação do histórico completo
- **WHEN** um usuário envia `GET /conversations/{id}/messages`
- **THEN** o sistema retorna `200 OK` com lista de mensagens ordenadas por `created_at` ASC
