## 1. Mover diretório

- [x] 1.1 `git mv app/backend-api/local app/local`
- [x] 1.2 Conferir que `docker-compose.yml` continua referenciando `./init.sql` corretamente (path relativo, não deve precisar mudar)

## 2. Ajustar start_local.sh

- [x] 2.1 Trocar `ENV_FILE="$LOCAL_DIR/../.env"` por `BACKEND_ENV_FILE="$LOCAL_DIR/../backend-api/.env"` e `FRONTEND_ENV_FILE="$LOCAL_DIR/../frontend/.env"`
- [x] 2.2 Atualizar leitura de `BUCKET_NAME` (hoje via `grep` no `ENV_FILE`) pra usar `BACKEND_ENV_FILE`
- [x] 2.3 Atualizar o bloco de `sed` existente (COGNITO_USER_POOL_ID, COGNITO_CLIENT_ID, S3_BUCKET_NAME, SQS_INGESTION_QUEUE_URL) pra gravar em `BACKEND_ENV_FILE`
- [x] 2.4 Adicionar validação: se `FRONTEND_ENV_FILE` não existir, falhar com mensagem clara (sem criar o arquivo)
- [x] 2.5 Adicionar bloco de `sed` gravando em `FRONTEND_ENV_FILE`: `VITE_COGNITO_USER_POOL_ID=$POOL_ID`, `VITE_COGNITO_CLIENT_ID=$CLIENT_ID`, `VITE_COGNITO_AUTHORITY=$ENDPOINT_URL/$POOL_ID`
- [x] 2.6 Atualizar mensagem final de "Pronto" pra mencionar os dois `.env` atualizados

## 3. Ajustar login.sh

- [x] 3.1 Trocar `ENV_FILE="$LOCAL_DIR/../.env"` por `$LOCAL_DIR/../backend-api/.env`

## 4. Atualizar referências no repo

- [x] 4.1 Atualizar `README.md` linha do comando (`./app/backend-api/local/start_local.sh` → `./app/local/start_local.sh`)
- [x] 4.2 Atualizar `openspec/changes/fix-users-email-unique-constraint/proposal.md` (path de `init.sql`)
- [x] 4.3 Atualizar `openspec/changes/fix-users-email-unique-constraint/tasks.md` (path de `init.sql`)

## 5. Validar

- [ ] 5.1 Rodar `docker compose -f app/local/docker-compose.yml down -v` pra zerar a stack
- [ ] 5.2 Rodar `app/local/start_local.sh` do zero e confirmar que `app/backend-api/.env` e `app/frontend/.env` ficam com os mesmos `POOL_ID`/`CLIENT_ID`
- [ ] 5.3 Rodar `app/local/start_local.sh` de novo (pool/client já existentes) e confirmar que os dois `.env` continuam corretos, sem erro
- [ ] 5.4 Rodar `app/local/login.sh` e confirmar que ainda gera token válido
