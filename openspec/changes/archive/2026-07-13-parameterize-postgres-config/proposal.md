## Why

O backend-api hoje só tem `spring.application.name=notebooklm` configurado — nenhum valor de datasource, AWS ou LLM está externalizado, e não existe stack local para subir Postgres+pgvector. Sem isso, não dá para rodar o app localmente nem preparar o deploy futuro em cloud (ECS/RDS) seguindo 12-factor (config via env, paridade dev/prod, backing services como recurso anexável).

## What Changes

- `application.properties` é substituído por `application.yml` no backend-api, lendo todo valor de configuração (datasource, AWS, LLM) via env var com default de desenvolvimento, sem nenhum valor hardcoded
- Novo `docker-compose.yml` subindo Postgres com extensão pgvector (`pgvector/pgvector:pg18`, última versão major disponível) com volume nomeado e healthcheck, **e** Floci (`floci/floci:latest`) emulando S3/SQS/Cognito localmente
- Novo script SQL de bootstrap (DDL já documentado em `DOMAIN.md`) montado via `docker-entrypoint-initdb.d`, localizado em `app/backend-api/local/` — não em `infra/`, que fica reservado para recursos Terraform futuros
- Novo `.env.example` documentando todas as env vars necessárias, incluindo endpoint do Floci, sem valores reais

Escopo desta mudança é só infraestrutura de desenvolvimento (compose + config parametrizada). Código de integração com S3/SQS/Cognito (clients, beans, uso do endpoint do Floci) fica para a próxima spec — aqui só garantimos que o serviço já sobe e está disponível em `localhost:4566`.

Fora de escopo (decidido deliberadamente para não sobre-engenheirar antes da hora):
- Ferramenta de migration (Flyway/Liquibase) — schema aplicado via script SQL simples por enquanto
- Código Java que integra com S3/SQS/Cognito via Floci — implementado na próxima spec (`source-ingestion`/`auth`)
- Profiles Spring separados (`application-local.yml`) — env vars nativas do Spring já resolvem override sem arquivo extra

## Capabilities

### New Capabilities

- `local-dev-environment`: Configuração parametrizada do backend-api (12-factor config) e stack Docker Compose local com Postgres+pgvector para desenvolvimento

### Modified Capabilities

(nenhuma — não há specs existentes ainda sobre configuração/infra)

## Impact

- Arquivos removidos: `app/backend-api/src/main/resources/application.properties`
- Arquivos novos: `app/backend-api/src/main/resources/application.yml`, `docker-compose.yml` (raiz do repo), `app/backend-api/local/init.sql`, `.env.example` (raiz do repo)
- Nenhum código de domínio/negócio alterado — mudança restrita a configuração e ambiente de desenvolvimento
- Prepara terreno para deploy futuro: mesmos nomes de env var usados aqui serão injetados via ECS task definition em produção
