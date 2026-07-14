## Context

`app/backend-api/src/main/resources/application.properties` hoje só tem `spring.application.name=notebooklm`. Nada de datasource, AWS ou LLM está configurado — impossível rodar localmente ou preparar deploy em ECS/RDS. `CLAUDE.md` já documenta a tabela de env vars alvo (`AWS_REGION`, `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `S3_BUCKET_NAME`, `SQS_INGESTION_QUEUE_URL`, `SPRING_AI_OPENAI_*`, `SPRING_DATASOURCE_*`), mas nenhuma está wired na aplicação. `infra/` está vazio e reservado para Terraform (fora de escopo desta mudança). Nenhum código de S3/SQS/Cognito existe ainda no backend — só o esqueleto Spring Boot padrão; essa integração é a próxima spec, esta mudança só prepara o ambiente (config + serviços locais).

## Goals / Non-Goals

**Goals:**
- Toda config de datasource, AWS e LLM lida via env var com default de desenvolvimento (12-factor Config), em `application.yml`
- Stack Postgres+pgvector local via Docker Compose, na versão mais recente disponível
- Floci disponível localmente emulando S3/SQS/Cognito, pronto para a próxima spec plugar código real
- Schema do banco aplicado automaticamente ao subir o container local, usando o DDL já existente em `DOMAIN.md`

**Non-Goals:**
- Ferramenta de migration (Flyway/Liquibase) — adiado; schema único, sem time ainda colidindo em mudanças concorrentes
- Código Java que integra com S3/SQS/Cognito (clients, beans, endpoint override do SDK apontando pro Floci) — próxima spec; aqui só o serviço sobe
- Profiles Spring dedicados (`application-local.yml`, `application-prod.yml`) — Spring Boot já faz bind de env var (`SPRING_DATASOURCE_URL`) para a property (`spring.datasource.url`) nativamente; um arquivo por ambiente não adiciona comportamento, só mais um arquivo pra manter
- Provisionamento de infra AWS (RDS, ECS task definition) — fica para os recursos Terraform em `infra/`, fora desta mudança

## Decisions

**1. Um único `application.yml` (não `.properties`) com placeholders `${VAR:default}`, sem profiles.**
YAML escolhido por instrução explícita do usuário — estrutura hierárquica fica mais legível para os blocos `spring.datasource`, `spring.ai`, e agrupamento de vars AWS. Alternativa considerada: `application-local.yml` + `application-prod.yml` (profiles separados). Rejeitada — Spring já resolve env var → property automaticamente; profile separado só duplicaria as mesmas chaves sem ganhar nenhum comportamento novo.

**2. Docker Compose com Postgres (`pgvector/pgvector:pg18`) e Floci (`floci/floci:latest`), serviço já configurado — só sem código de integração ainda.**
Ajuste em relação à exploração original: Floci entra já nesta mudança como serviço de infraestrutura local (porta `4566`), mas o código Java que efetivamente chama S3/SQS/Cognito via esse endpoint fica para a próxima spec (`source-ingestion`/`auth`). Isso evita que a spec seguinte precise voltar e mexer no compose — só adiciona os clients/beans em cima do que já está de pé.

**2.1 Versões: sempre a mais recente disponível de cada imagem.**
Por instrução explícita do usuário: `pgvector/pgvector:pg18` (Postgres 18, mais recente suportada pela imagem) e `floci/floci:latest`. **Trade-off registrado**: `ARCHITECTURE.md`/`CLAUDE.md` especificam Postgres 16 para o RDS em produção — usar pg18 localmente quebra dev/prod parity (factor X) até que a versão do RDS seja atualizada ou o compose seja fixado em pg16. Ver risco correspondente abaixo.

**3. Script SQL de bootstrap em `app/backend-api/local/`, montado via `docker-entrypoint-initdb.d`.**
Alternativa considerada: colocar em `infra/`. Rejeitada por instrução explícita do usuário — `infra/` é reservado para recursos Terraform; scripts de dev local pertencem ao módulo da aplicação que os usa.

**4. Sem ferramenta de migration.**
Alternativa considerada: Flyway. Adiado deliberadamente — schema ainda é único, sem versionamento incremental necessário. Script SQL bruto reaplicado via `docker-entrypoint-initdb.d` (só roda no primeiro boot do volume) resolve o caso de uso atual. Ao introduzir a primeira migração incremental de schema em produção, revisitar essa decisão.

**5. Produção usa env vars puras injetadas pela ECS task definition.**
App nunca importa SDK de Secrets Manager — só lê `System.getenv`/property placeholders. Rotação de secret e resolução de Parameter Store/Secrets Manager acontece inteiramente no nível de infra (Terraform + ECS), fora do código da aplicação.

## Risks / Trade-offs

- **Sem migration tool** → alterações de schema em produção exigem aplicar SQL manualmente (admin process, factor XII). Mitigação: documentar o procedimento; revisitar Flyway quando houver 2+ desenvolvedores tocando schema.
- **Postgres local em pg18 vs RDS em pg16 (documentado)** → quebra dev/prod parity; comportamento de extensão/sintaxe pode divergir entre versões major. Mitigação: revisitar quando o RDS for provisionado — alinhar a tag do compose com a versão real do RDS, ou atualizar o RDS alvo para pg18 se viável.
- **Floci sobe sem nenhum código consumindo ainda** → serviço fica "parado" até a próxima spec plugar S3/SQS/Cognito; risco baixo, mas healthcheck do compose deve cobrir só o que existe hoje (Postgres), Floci não tem consumidor pra validar nesta mudança.
- **Script init.sql só roda no primeiro boot do volume Docker** → se o schema mudar, dev precisa `docker compose down -v` para recriar. Mitigação: documentar esse comportamento no README/task de setup.

## Migration Plan

Mudança é aditiva e local — sem impacto em ambiente já existente (não há deploy em produção ainda). Passos:
1. Substituir `application.properties` por `application.yml` com placeholders de env var
2. Criar `docker-compose.yml` (Postgres + Floci) e `app/backend-api/local/init.sql`
3. Criar `.env.example`
4. Validar subindo `docker compose up` e conectando o backend-api localmente

Rollback: reverter os arquivos criados/alterados; nenhum estado externo é afetado.

## Open Questions

- Quando `source-ingestion`/`auth` forem implementados (próxima spec), plugar os clients AWS SDK apontando pro endpoint do Floci já disponível.
- Quando houver necessidade de migração incremental de schema em produção, avaliar Flyway.
- Confirmar se o RDS de produção deve subir em pg18 (acompanhando o local) ou se o compose deve ser fixado em pg16 para manter parity com a decisão já documentada em ARCHITECTURE.md.
