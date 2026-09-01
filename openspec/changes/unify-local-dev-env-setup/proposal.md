## Why

O script de provisionamento local (`app/backend-api/local/start_local.sh`) mora dentro do backend, mas sobe infra compartilhada por backend e frontend (Postgres, Floci com Cognito/S3/SQS). Hoje ele só grava os IDs do Cognito provisionados em `app/backend-api/.env` — `app/frontend/.env` mantém os mesmos valores (`VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_CLIENT_ID`, `VITE_COGNITO_AUTHORITY`) copiados manualmente, e fica desatualizado toda vez que o pool/client é recriado do zero (ex: `docker compose down -v`). Mover o script pra raiz de `app/` e fazer ele configurar os dois `.env` juntos remove esse passo manual e a fonte de bugs de auth local quebrado por dessincronia.

## What Changes

- Move `app/backend-api/local/` inteiro (`docker-compose.yml`, `init.sql`, `start_local.sh`, `login.sh`) para `app/local/`
- `start_local.sh` passa a gravar em `app/backend-api/.env` (`COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `S3_BUCKET_NAME`, `SQS_INGESTION_QUEUE_URL` — igual hoje) **e** em `app/frontend/.env` (`VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_CLIENT_ID`, `VITE_COGNITO_AUTHORITY`) no mesmo passo
- `login.sh` ajusta o path relativo do `.env` do backend após a mudança de diretório
- Atualiza `README.md` com o novo caminho do script
- Atualiza referências a `app/backend-api/local/init.sql` na change pendente `fix-users-email-unique-constraint` (ainda não implementada)
- **BREAKING**: nenhuma — apenas reorganização de path e escrita adicional de env var; comportamento de provisionamento (Cognito/S3/SQS/Postgres) não muda

Fora de escopo (confirmado com o usuário): o script continua só provisionando infra + env — não sobe `mvnw spring-boot:run` nem `npm run dev`. Também não cria `.env` a partir de `.env.example` automaticamente — assume que ambos já existem (estado atual do repo).

## Capabilities

### Modified Capabilities
- `local-dev-environment`: o script de provisionamento local muda de diretório (`app/backend-api/local/` → `app/local/`) e passa a configurar o `.env` do frontend além do backend, mantendo os dois sincronizados com os IDs Cognito provisionados

## Impact

- `app/backend-api/local/*` → `app/local/*` (rename de diretório: `docker-compose.yml`, `init.sql`, `start_local.sh`, `login.sh`)
- `app/local/start_local.sh`: novo bloco de sed para `app/frontend/.env`, paths de `ENV_FILE` ajustados
- `app/local/login.sh`: path de `ENV_FILE` ajustado
- `README.md`: caminho do comando de start local atualizado
- `openspec/changes/fix-users-email-unique-constraint/proposal.md` e `tasks.md`: path de `init.sql` atualizado
