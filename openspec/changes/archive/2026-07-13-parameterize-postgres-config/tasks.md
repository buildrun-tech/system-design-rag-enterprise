## 1. Configuração parametrizada do backend-api

- [x] 1.1 Remover `application.properties` e criar `application.yml` em `app/backend-api/src/main/resources/`
- [x] 1.2 Configurar `spring.datasource.url/username/password` via `${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/notebooklm}`, `${SPRING_DATASOURCE_USERNAME:notebooklm}`, `${SPRING_DATASOURCE_PASSWORD:notebooklm}`
- [x] 1.3 Adicionar placeholders de AWS: `${AWS_REGION:us-east-1}`, `${AWS_ENDPOINT_URL:http://localhost:4566}`, `${COGNITO_USER_POOL_ID:}`, `${COGNITO_CLIENT_ID:}`, `${S3_BUCKET_NAME:}`, `${SQS_INGESTION_QUEUE_URL:}`
- [x] 1.4 Adicionar placeholders do provider LLM: `${SPRING_AI_OPENAI_BASE_URL:}`, `${SPRING_AI_OPENAI_API_KEY:}`, `${SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL:}`
- [x] 1.5 Conferir que nenhum valor sensível/real ficou hardcoded no `application.yml`

## 2. Stack Docker Compose local

- [x] 2.1 Criar `docker-compose.yml` na raiz do repo com serviço `postgres` usando a imagem mais recente de `pgvector/pgvector` (ex: `pgvector/pgvector:pg18`)
- [x] 2.2 Configurar volume nomeado para persistência de dados do Postgres
- [x] 2.3 Configurar healthcheck do serviço `postgres` (ex: `pg_isready`)
- [x] 2.4 Mapear env vars do container (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`) para os mesmos valores default usados no `application.yml`
- [x] 2.5 Adicionar serviço `floci` ao `docker-compose.yml` usando `floci/floci:latest`, expondo a porta `4566`
- [x] 2.6 Configurar volume (se necessário) para persistência de estado do Floci entre execuções

## 3. Script de bootstrap do schema

- [x] 3.1 Criar `app/backend-api/local/init.sql` com o DDL completo já documentado em `DOMAIN.md` (`CREATE EXTENSION vector`, tabelas `users`, `notebooks`, `sources`, `source_chunks`, `conversations`, `conversation_active_sources`, `conversation_messages`, índices incluindo HNSW)
- [x] 3.2 Montar `app/backend-api/local/init.sql` como volume `docker-entrypoint-initdb.d` no serviço `postgres` do `docker-compose.yml`

## 4. Documentação de variáveis de ambiente

- [x] 4.1 Criar `.env.example` na raiz do repo listando todas as variáveis consumidas pelo backend-api, incluindo `AWS_ENDPOINT_URL` apontando pro Floci, sem valores reais
- [x] 4.2 Adicionar `.env` ao `.gitignore` (se ainda não estiver) para evitar commit acidental de credenciais

## 5. Validação

- [x] 5.1 Rodar `docker compose up` e confirmar que o Postgres sobe saudável com a extensão `vector` e o schema criado
- [x] 5.2 Rodar `docker compose up` e confirmar que o Floci sobe e responde em `http://localhost:4566`
- [x] 5.3 Rodar `./mvnw spring-boot:run` apontando para o Postgres local (via `.env` copiado de `.env.example`) e confirmar que a aplicação sobe sem erro de conexão com o banco
