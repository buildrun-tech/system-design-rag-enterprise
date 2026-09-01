## MODIFIED Requirements

### Requirement: Envio de mensagem com resposta em streaming
O sistema SHALL enviar mensagens do usuário via `POST /api/v1/conversations/{conversationId}/messages` e renderizar a resposta do assistente token a token conforme o stream SSE chega, formatando o conteúdo como markdown (negrito, listas, código, links, quebras de linha) e exibindo indicação visual de carregamento enquanto nenhum token chegou.

#### Scenario: Envio de mensagem
- **WHEN** o usuário digita uma mensagem e aciona o envio
- **THEN** o sistema adiciona a mensagem do usuário à lista imediatamente e abre a conexão SSE autenticada para receber a resposta

#### Scenario: Aguardando primeira resposta
- **WHEN** a mensagem do assistente foi criada e nenhum token chegou ainda
- **THEN** o sistema exibe um spinner na bolha da mensagem do assistente

#### Scenario: Recebendo tokens do stream
- **WHEN** o servidor envia eventos `data: {"token": "..."}`
- **THEN** o sistema concatena os tokens na mensagem do assistente em construção, renderizando o conteúdo acumulado como markdown em tempo real

#### Scenario: Fim do stream
- **WHEN** o servidor envia `data: {"done": true, "messageId": "..."}`
- **THEN** o sistema finaliza a mensagem do assistente como concluída

#### Scenario: Erro durante o stream
- **WHEN** o servidor envia `data: {"error": "STREAM_ERROR"}` ou a conexão falha
- **THEN** o sistema exibe indicação de erro na mensagem e permite tentar reenviar

## ADDED Requirements

### Requirement: Navegação de volta para a lista de notebooks
O sistema SHALL exibir um botão "voltar" no workspace que navega para a tela de listagem de notebooks (`/notebooks`).

#### Scenario: Usuário aciona voltar
- **WHEN** o usuário clica no botão "voltar" no workspace
- **THEN** o sistema navega para `/notebooks`

### Requirement: Criação e troca de conversa dentro do notebook
O sistema SHALL permitir criar uma nova conversa dentro do notebook atual via `POST /api/v1/notebooks/{notebookId}/conversations` e trocar entre conversas existentes, obtidas via `GET /api/v1/notebooks/{notebookId}/conversations`.

#### Scenario: Criar novo chat
- **WHEN** o usuário aciona "novo chat" no workspace
- **THEN** o sistema cria uma nova conversa, torna-a a conversa ativa e exibe o chat vazio

#### Scenario: Trocar para conversa existente
- **WHEN** o usuário seleciona outra conversa no seletor de conversas
- **THEN** o sistema torna essa conversa a ativa e carrega seu histórico via `GET /api/v1/conversations/{conversationId}/messages`

#### Scenario: Trocar de conversa durante streaming ativo
- **WHEN** o usuário troca de conversa enquanto uma resposta ainda está sendo recebida via SSE
- **THEN** o sistema encerra a conexão SSE em andamento antes de carregar a nova conversa
