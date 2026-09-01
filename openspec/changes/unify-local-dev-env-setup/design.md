## Context

`app/backend-api/local/` guarda infra compartilhada (Postgres+pgvector, Floci) dentro do módulo backend. `start_local.sh` provisiona Cognito/S3/SQS e grava os IDs só em `app/backend-api/.env`; `app/frontend/.env` tem os mesmos valores hardcoded e dessincroniza sempre que a stack é recriada do zero.

## Goals / Non-Goals

**Goals:**
- Um único script (`app/local/start_local.sh`) provisiona infra local e mantém `app/backend-api/.env` e `app/frontend/.env` sincronizados com os mesmos IDs Cognito
- Local dos scripts reflete que a infra é compartilhada, não exclusiva do backend

**Non-Goals:**
- Subir as aplicações (`mvnw spring-boot:run`, `npm run dev`) — continua manual, dois terminais
- Criar `.env` a partir de `.env.example` automaticamente — assume que ambos já existem
- Mudar comportamento de provisionamento (Cognito user pool/client/bucket/fila) — só o alvo dos writes muda

## Decisions

**Mover `local/` inteiro pra `app/local/` (não deixar `docker-compose.yml`/`init.sql` em `backend-api/`)**
Alternativa considerada: só mover `start_local.sh`, deixar `docker-compose.yml`/`init.sql` em `backend-api/local/`. Rejeitada — separaria script do compose que ele invoca via path relativo (`$LOCAL_DIR/docker-compose.yml`), forçando dois lugares pra entender a mesma stack. Mover tudo junto mantém o diretório coeso.

**`ENV_FILE` vira dois paths explícitos, sem parametrizar**
`start_local.sh` e `login.sh` hoje usam `$LOCAL_DIR/../.env` (funciona porque `local/` está dentro de `backend-api/`). Após o move, isso teria que virar `$LOCAL_DIR/../backend-api/.env` (perde o segundo `.env`). Em vez de generalizar pra N arquivos configuráveis (over-engineering pra 2 arquivos fixos), declara os dois paths como variáveis nomeadas (`BACKEND_ENV_FILE`, `FRONTEND_ENV_FILE`) e faz sed em cada um com as chaves que já existem em cada `.env.example`.

**Sed no frontend cobre só as 3 chaves que dependem do provisionamento**
`VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_CLIENT_ID`, `VITE_COGNITO_AUTHORITY` (monta a URL com `$ENDPOINT_URL/$POOL_ID`). Demais chaves do frontend `.env` (`VITE_API_BASE_URL`, `VITE_COGNITO_REDIRECT_URI`, `VITE_COGNITO_ENDPOINT`, `VITE_ENABLE_DIRECT_SIGNUP`) não vêm do provisionamento — script não mexe nelas.

## Risks / Trade-offs

- [Script falha se `app/frontend/.env` não existir ainda (sed em arquivo inexistente)] → mesma pré-condição que já existe hoje pro backend (`.env` deve existir antes de rodar); documentar no README, sem criar fallback automático (fora de escopo por decisão do usuário)
- [Change pendente `fix-users-email-unique-constraint` referencia `app/backend-api/local/init.sql`, ainda não implementada] → atualizar os paths nela como parte desta change, antes que seja implementada com o path velho

## Migration Plan

1. `git mv app/backend-api/local app/local`
2. Ajustar `ENV_FILE` em `start_local.sh` e `login.sh` pros dois novos paths relativos
3. Adicionar bloco de sed pro frontend em `start_local.sh`
4. Atualizar `README.md` e `openspec/changes/fix-users-email-unique-constraint/*`
5. Rodar `start_local.sh` do zero localmente e validar que os dois `.env` ficam com os mesmos IDs
