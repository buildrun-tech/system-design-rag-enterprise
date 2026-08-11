## ADDED Requirements

### Requirement: Client Cognito local com USER_PASSWORD_AUTH
O `start_local.sh` SHALL garantir que o app client Cognito local tenha o auth flow `ALLOW_USER_PASSWORD_AUTH` habilitado, além do `ADMIN_NO_SRP_AUTH` já existente, e seja um client público (sem secret), para suportar chamadas diretas do SDK no browser.

#### Scenario: Provisionar client do zero
- **WHEN** `start_local.sh` roda e o app client `notebooklm-local-client` ainda não existe
- **THEN** o client é criado com `ALLOW_USER_PASSWORD_AUTH` e `ADMIN_NO_SRP_AUTH` habilitados e sem client secret

#### Scenario: Client já existente sem USER_PASSWORD_AUTH
- **WHEN** `start_local.sh` roda e o app client já existe mas não tem `ALLOW_USER_PASSWORD_AUTH` habilitado
- **THEN** o script atualiza o client via `update-user-pool-client` para incluir o auth flow, sem recriar o client (preservando o `CLIENT_ID` já salvo em `.env`)

### Requirement: Confirmação de cadastro sem passo manual no pool local
O frontend SHALL confirmar automaticamente o cadastro direto (`ConfirmSignUp` com código fixo) logo após o `SignUp`, sem exigir que o usuário digite código de verificação, aproveitando que o emulador floci não valida o conteúdo do código de confirmação.

#### Scenario: Cadastro via SignUp direto no pool local
- **WHEN** um usuário é criado via `SignUp` no user pool local e o frontend chama `ConfirmSignUp` em seguida
- **THEN** o usuário fica com status `CONFIRMED` sem que nenhuma pessoa digite código de verificação
