## Purpose

Fornecer o workspace de um notebook, exibindo suas sources e a conversa com o assistente, incluindo envio de mensagens e recebimento de respostas em streaming via SSE.

## Requirements

### Requirement: Listagem de sources do notebook
O sistema SHALL exibir, no painel "sources" da Tela 3, as sources do notebook obtidas de `GET /api/v1/notebooks/{notebookId}/sources`, incluindo seu status de processamento.

#### Scenario: Notebook com sources
- **WHEN** o usuário abre o workspace de um notebook com sources cadastradas
- **THEN** o sistema lista cada source com nome e indicação visual do status (`PENDING`/`PROCESSING`/`READY`/`FAILED`)

#### Scenario: Notebook sem sources
- **WHEN** o usuário abre o workspace de um notebook sem sources
- **THEN** o sistema exibe o painel de sources vazio sem erro

### Requirement: Conversa e histórico de mensagens
O sistema SHALL exibir o histórico de mensagens da conversa ativa do notebook, obtido de `GET /api/v1/conversations/{conversationId}/messages`.

#### Scenario: Primeiro acesso ao workspace sem conversa existente
- **WHEN** o usuário abre o workspace de um notebook que ainda não tem conversa
- **THEN** o sistema cria uma conversa via `POST /api/v1/notebooks/{notebookId}/conversations` (todas as sources `READY` ativas) antes de exibir o chat

#### Scenario: Workspace com histórico existente
- **WHEN** o usuário abre o workspace de um notebook com conversa e mensagens prévias
- **THEN** o sistema exibe as mensagens em ordem cronológica, diferenciando remetente (`user`/`assistant`)

### Requirement: Envio de mensagem com resposta em streaming
O sistema SHALL enviar mensagens do usuário via `POST /api/v1/conversations/{conversationId}/messages` e renderizar a resposta do assistente token a token conforme o stream SSE chega.

#### Scenario: Envio de mensagem
- **WHEN** o usuário digita uma mensagem e aciona o envio
- **THEN** o sistema adiciona a mensagem do usuário à lista imediatamente e abre a conexão SSE autenticada para receber a resposta

#### Scenario: Recebendo tokens do stream
- **WHEN** o servidor envia eventos `data: {"token": "..."}`
- **THEN** o sistema concatena os tokens na mensagem do assistente em construção, atualizando a tela em tempo real

#### Scenario: Fim do stream
- **WHEN** o servidor envia `data: {"done": true, "messageId": "..."}`
- **THEN** o sistema finaliza a mensagem do assistente como concluída

#### Scenario: Erro durante o stream
- **WHEN** o servidor envia `data: {"error": "STREAM_ERROR"}` ou a conexão falha
- **THEN** o sistema exibe indicação de erro na mensagem e permite tentar reenviar
