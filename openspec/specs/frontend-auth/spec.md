## Purpose

Fornecer o fluxo de autenticação do frontend via AWS Cognito Hosted UI, mantendo a sessão do usuário e anexando o access token a todas as requisições à API, para que apenas usuários autenticados acessem as telas de notebooks e workspace.

## Requirements

### Requirement: Login via Cognito Hosted UI
O sistema SHALL apresentar uma tela de login com botões "Login Google" e "Login Github" que redirecionam para o Cognito Hosted UI usando fluxo OAuth2 Authorization Code + PKCE.

#### Scenario: Usuário não autenticado acessa a aplicação
- **WHEN** um usuário sem sessão ativa abre a aplicação
- **THEN** o sistema exibe a tela de login (Tela 1) com as opções "Login Google" e "Login Github"

#### Scenario: Usuário escolhe provedor de login
- **WHEN** o usuário clica em "Login Google" ou "Login Github"
- **THEN** o sistema redireciona para o Cognito Hosted UI com o `identity_provider` correspondente

#### Scenario: Callback de autenticação bem-sucedida
- **WHEN** o Cognito redireciona de volta com um `code` válido
- **THEN** o sistema troca o code por tokens (access, id, refresh), armazena a sessão e navega para a tela de notebooks

### Requirement: Sessão autenticada em requisições à API
O sistema SHALL incluir o access token do Cognito como header `Authorization: Bearer <token>` em toda requisição à API backend.

#### Scenario: Requisição autenticada
- **WHEN** o frontend faz qualquer chamada a `/api/v1/**`
- **THEN** a requisição inclui o header `Authorization: Bearer <access_token>` da sessão ativa

#### Scenario: Token expirado
- **WHEN** o access token expira e existe refresh token válido
- **THEN** o sistema renova o token silenciosamente antes de repetir a requisição

### Requirement: Rotas protegidas
O sistema SHALL impedir acesso às telas de notebooks e workspace sem sessão autenticada.

#### Scenario: Acesso direto a rota protegida sem sessão
- **WHEN** um usuário sem sessão ativa navega diretamente para `/notebooks` ou `/notebooks/:id`
- **THEN** o sistema redireciona para a tela de login

### Requirement: Logout
O sistema SHALL permitir encerrar a sessão do usuário.

#### Scenario: Usuário faz logout
- **WHEN** o usuário aciona a ação de logout (ícone "p" no canto superior direito das telas 2 e 3)
- **THEN** o sistema limpa a sessão local e redireciona para a tela de login
