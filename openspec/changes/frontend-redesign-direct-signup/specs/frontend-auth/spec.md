## ADDED Requirements

### Requirement: Cadastro direto por email e senha
Quando a flag de ambiente `VITE_ENABLE_DIRECT_SIGNUP` estiver habilitada, o sistema SHALL exibir na tela de login um formulário de cadastro por email e senha que chama a API Cognito `SignUp` diretamente via SDK, sem passar pelo Hosted UI.

#### Scenario: Flag habilitada exibe formulário
- **WHEN** `VITE_ENABLE_DIRECT_SIGNUP=true` e o usuário abre a tela de login
- **THEN** o sistema exibe, além dos botões de login social, um formulário com campos de email e senha e ação "Cadastrar"

#### Scenario: Flag desabilitada oculta formulário
- **WHEN** `VITE_ENABLE_DIRECT_SIGNUP` não está definida ou é `false`
- **THEN** a tela de login exibe apenas os botões de login social, sem o formulário direto

#### Scenario: Cadastro bem-sucedido
- **WHEN** o usuário preenche email e senha válidos e submete o formulário de cadastro
- **THEN** o sistema chama `SignUp` na API Cognito e, em caso de sucesso, autentica o usuário automaticamente sem exigir código de confirmação

### Requirement: Login direto por email e senha
Quando a flag de ambiente `VITE_ENABLE_DIRECT_SIGNUP` estiver habilitada, o sistema SHALL permitir login por email e senha usando `InitiateAuth` (`USER_PASSWORD_AUTH`) diretamente contra a API Cognito.

#### Scenario: Login direto bem-sucedido
- **WHEN** o usuário submete email e senha válidos no formulário de login direto
- **THEN** o sistema obtém access/id/refresh token via `InitiateAuth` e navega para a tela de notebooks, com o token disponível para as chamadas à API nos mesmos moldes do fluxo Hosted UI

#### Scenario: Login direto com credenciais inválidas
- **WHEN** o usuário submete email ou senha incorretos
- **THEN** o sistema exibe mensagem de erro sem navegar para a tela de notebooks
