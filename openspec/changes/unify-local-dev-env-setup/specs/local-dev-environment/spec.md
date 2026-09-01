## ADDED Requirements

### Requirement: Scripts de provisionamento local na raiz de app/

Os scripts de provisionamento da stack local (`start_local.sh`, `login.sh`) e seus artefatos de infra (`docker-compose.yml`, `init.sql`) SHALL residir em `app/local/`, fora de qualquer módulo específico (`backend-api/` ou `frontend/`), refletindo que a infra provisionada (Postgres, Floci) é compartilhada pelos dois.

#### Scenario: Localizar o script de provisionamento
- **WHEN** um desenvolvedor procura como subir a stack local
- **THEN** encontra `app/local/start_local.sh`, `app/local/login.sh`, `app/local/docker-compose.yml` e `app/local/init.sql` num único diretório na raiz de `app/`

### Requirement: Provisionamento sincroniza .env do backend e do frontend

`start_local.sh` SHALL gravar os IDs Cognito provisionados tanto em `app/backend-api/.env` quanto em `app/frontend/.env`, mantendo os dois arquivos consistentes com o mesmo user pool e client provisionados na mesma execução.

#### Scenario: Provisionar do zero
- **WHEN** `start_local.sh` roda e cria um novo user pool e client Cognito
- **THEN** `app/backend-api/.env` recebe `COGNITO_USER_POOL_ID` e `COGNITO_CLIENT_ID`, e `app/frontend/.env` recebe `VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_CLIENT_ID` e `VITE_COGNITO_AUTHORITY` (`http://localhost:4566/<POOL_ID>`) com os mesmos valores provisionados

#### Scenario: Reprovisionar com pool/client já existentes
- **WHEN** `start_local.sh` roda novamente e encontra o user pool e client já existentes (mesmo nome)
- **THEN** os dois `.env` são atualizados com os IDs existentes, sem exigir edição manual em nenhum dos dois arquivos

#### Scenario: .env do frontend ausente
- **WHEN** `app/frontend/.env` não existe no momento em que `start_local.sh` roda
- **THEN** o script falha com mensagem indicando que o arquivo precisa existir antes (mesma pré-condição já aplicada hoje a `app/backend-api/.env`), sem criar o arquivo automaticamente
