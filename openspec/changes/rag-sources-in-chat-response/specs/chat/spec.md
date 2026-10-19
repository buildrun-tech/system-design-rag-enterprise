## MODIFIED Requirements

### Requirement: Enviar mensagem e receber resposta via SSE
O sistema SHALL aceitar mensagens do usuário e retornar a resposta do LLM como stream de tokens via SSE (Server-Sent Events), informando ao final quais sources foram usadas como contexto.

#### Scenario: Mensagem com RAG bem-sucedido
- **WHEN** um usuário envia `POST /conversations/{id}/messages` com `{"content": "..."}`
- **THEN** o sistema realiza similarity search no pgvector filtrado pelas sources ativas
- **AND** monta prompt com os chunks recuperados como contexto
- **AND** chama o LLM provider (streaming)
- **AND** retorna `200 OK` com `Content-Type: text/event-stream`
- **AND** cada token é enviado como `data: {"token": "..."}` via SSE
- **AND** ao finalizar, envia `data: {"done": true, "messageId": "...", "sources": [{"sourceId": "...", "name": "...", "chunks": [{"chunkIndex": 0, "snippet": "..."}]}]}` e fecha o stream

#### Scenario: Sources distintas com chunks no evento done
- **WHEN** mais de um chunk recuperado pertence à mesma source
- **THEN** o evento `done` lista cada source uma única vez, na ordem da primeira ocorrência nos resultados
- **AND** cada source contém `sourceId`, `name` e `chunks` com os chunks recuperados dela
- **AND** cada chunk contém `chunkIndex` e `snippet`

#### Scenario: Snippet truncado
- **WHEN** o texto de um chunk recuperado excede 300 caracteres
- **THEN** o `snippet` é truncado em limite de palavra com no máximo ~300 caracteres

#### Scenario: Resposta sem contexto recuperado
- **WHEN** nenhum chunk relevante é recuperado
- **THEN** o evento `done` inclui `"sources": []`

#### Scenario: Mensagem sem sources ativas ou READY
- **WHEN** um usuário envia mensagem em uma conversa sem sources ativas com status READY
- **THEN** o sistema responde normalmente via LLM sem contexto RAG
- **AND** inclui aviso no response que nenhuma source estava disponível
- **AND** o evento `done` inclui `"sources": []`

### Requirement: Histórico de conversa persistido
O sistema SHALL persistir todas as mensagens (usuário e assistente) no banco de dados, vinculadas à conversa, incluindo as sources usadas em cada resposta do assistente.

#### Scenario: Persistência de mensagem do usuário
- **WHEN** o usuário envia uma mensagem
- **THEN** a mensagem é persistida em `conversation_messages` com `role: user` e `sources: []`

#### Scenario: Persistência de resposta do assistente
- **WHEN** o LLM finaliza o streaming da resposta
- **THEN** a resposta completa é persistida em `conversation_messages` com `role: assistant`
- **AND** as sources usadas são gravadas na mensagem como snapshot `[{sourceId, name, chunks:[{chunkIndex, snippet}]}]`

#### Scenario: Recuperação do histórico para contexto
- **WHEN** o sistema monta o prompt para o LLM
- **THEN** as últimas N mensagens da conversa são incluídas no prompt como histórico

### Requirement: Buscar mensagens de uma conversa
O sistema SHALL retornar as mensagens de uma conversa específica em ordem cronológica, incluindo as sources usadas em cada mensagem.

#### Scenario: Recuperação do histórico completo
- **WHEN** um usuário envia `GET /conversations/{id}/messages`
- **THEN** o sistema retorna `200 OK` com lista de mensagens ordenadas por `created_at` ASC
- **AND** cada mensagem contém o campo `sources` (`[{sourceId, name, chunks:[{chunkIndex, snippet}]}]`)

#### Scenario: Mensagem sem sources
- **WHEN** a mensagem é de `role: user`, de resposta sem contexto, ou anterior a esta funcionalidade
- **THEN** o campo `sources` é `[]`
