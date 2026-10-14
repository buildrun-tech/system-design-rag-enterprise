## MODIFIED Requirements

### Requirement: Sessão autenticada em requisições à API
O sistema SHALL incluir o access token do Cognito como header `Authorization: Bearer <token>` em toda requisição à API backend, e SHALL encerrar a sessão quando a API rejeitar o token com `401`.

#### Scenario: Requisição autenticada
- **WHEN** o frontend faz qualquer chamada a `/api/v1/**`
- **THEN** a requisição inclui o header `Authorization: Bearer <access_token>` da sessão ativa

#### Scenario: Token expirado
- **WHEN** o access token expira e existe refresh token válido
- **THEN** o sistema renova o token silenciosamente antes de repetir a requisição

#### Scenario: API rejeita o token com 401
- **WHEN** qualquer chamada a `/api/v1/**` (REST ou stream SSE do chat) responde `401`, independentemente do corpo da resposta
- **THEN** o sistema limpa a sessão local e redireciona para a tela de login

#### Scenario: Falha na renovação silenciosa do token
- **WHEN** a renovação silenciosa do token OIDC falha
- **THEN** o sistema limpa a sessão local e redireciona para a tela de login

### Requirement: Rotas protegidas
O sistema SHALL impedir acesso às telas de notebooks e workspace sem sessão autenticada válida e não expirada.

#### Scenario: Acesso direto a rota protegida sem sessão
- **WHEN** um usuário sem sessão ativa navega diretamente para `/notebooks` ou `/notebooks/:id`
- **THEN** o sistema redireciona para a tela de login

#### Scenario: Reload com sessão direta expirada
- **WHEN** a aplicação carrega com uma sessão direta armazenada cujo access token já expirou (`exp` no passado ou payload ilegível)
- **THEN** o sistema descarta a sessão armazenada e redireciona para a tela de login
