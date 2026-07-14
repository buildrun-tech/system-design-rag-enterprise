## ADDED Requirements

### Requirement: Login via Google ou GitHub com AWS Cognito
O sistema SHALL suportar autenticação via OAuth2 com provedores Google e GitHub, delegando o fluxo ao AWS Cognito Hosted UI. Após autenticação, o Cognito emite um JWT (access_token) que é usado em todas as requisições subsequentes.

#### Scenario: Usuário faz login com Google
- **WHEN** o usuário clica em "Entrar com Google" no frontend
- **THEN** o frontend redireciona para o Cognito Hosted UI com o fluxo OAuth2 PKCE
- **AND** após autenticação bem-sucedida, o Cognito redireciona de volta com `code`
- **AND** o frontend troca o `code` pelo `access_token` e `id_token`

#### Scenario: Usuário faz login com GitHub
- **WHEN** o usuário clica em "Entrar com GitHub" no frontend
- **THEN** o mesmo fluxo OAuth2 PKCE ocorre via Cognito com o provedor GitHub

### Requirement: Validação stateless de JWT em cada requisição
O backend SHALL validar o JWT do Cognito em cada requisição autenticada sem manter estado de sessão. A validação SHALL verificar assinatura, expiração, e audience usando o JWKS público do Cognito.

#### Scenario: Requisição com JWT válido
- **WHEN** o cliente envia uma requisição com `Authorization: Bearer <valid_jwt>`
- **THEN** o backend valida o token via JWKS do Cognito
- **AND** extrai o `sub` do Cognito como identificador do usuário
- **AND** processa a requisição normalmente

#### Scenario: Requisição com JWT expirado ou inválido
- **WHEN** o cliente envia uma requisição com JWT expirado, mal-formado, ou de audience incorreto
- **THEN** o backend retorna `401 Unauthorized` com corpo `{"error": "invalid_token"}`

#### Scenario: Requisição sem JWT em endpoint protegido
- **WHEN** o cliente acessa um endpoint protegido sem cabeçalho `Authorization`
- **THEN** o backend retorna `401 Unauthorized`

### Requirement: Criação automática de perfil de usuário no primeiro acesso
O sistema SHALL criar automaticamente um registro de usuário na tabela `users` na primeira vez que um JWT válido é recebido, usando o `sub` do Cognito como identificador único.

#### Scenario: Primeiro acesso de um novo usuário
- **WHEN** um JWT válido é recebido de um usuário cujo `sub` não existe na tabela `users`
- **THEN** o sistema cria um registro em `users` com `cognito_sub`, `email`, e `name` extraídos do token
- **AND** a requisição original prossegue normalmente
