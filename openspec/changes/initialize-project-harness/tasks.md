## 1. Configuração do openspec/config.yaml

- [x] 1.1 Preencher campo `context` no `openspec/config.yaml` com stack tecnológica, domínio, decisões arquiteturais e convenções do projeto

## 2. CLAUDE.md — Guia do codebase

- [x] 2.1 Criar `CLAUDE.md` na raiz com seções: visão geral do projeto, stack tecnológica (Java 21, Spring Boot 3, Spring AI, AWS SDK v2), estrutura de módulos por feature (`auth`, `notebook`, `source`, `ingestion`, `chat`, `shared`)
- [x] 2.2 Adicionar seção de comandos de desenvolvimento no `CLAUDE.md`: build (`./mvnw package`), run (`./mvnw spring-boot:run`), test (`./mvnw test`), lint/format
- [x] 2.3 Adicionar seção de convenções no `CLAUDE.md`: padrão de nomenclatura, estilo de DTOs (records Java), tratamento de erros (GlobalExceptionHandler), padrão de respostas REST

## 3. ARCHITECTURE.md — Diagrama de infraestrutura AWS

- [x] 3.1 Criar `ARCHITECTURE.md` com diagrama ASCII da infraestrutura AWS: Browser → API Gateway (HTTP API) → ALB → ECS (Spring Boot stateless) → Cognito / S3 / RDS PostgreSQL+pgvector / SQS → LLM Provider (Bedrock/OpenRouter)
- [x] 3.2 Adicionar fluxo de dados de upload de source: POST /sources → S3 upload → SQS publish → consumer (extração → chunking → embedding → pgvector)
- [x] 3.3 Adicionar fluxo de dados de chat: POST /messages → pgvector similarity search → prompt montado com contexto → LLM streaming → SSE response

## 4. ARCHITECTURE.md — Modelo de dados (ERD e DDL)

- [x] 4.1 Documentar ERD simplificado em ASCII: `users` ←1:N→ `notebooks` ←1:N→ `sources` ←1:N→ `source_chunks`; `notebooks` ←1:N→ `conversations` ←1:N→ `conversation_messages`; `conversations` ←M:N→ `sources` (via `conversation_active_sources`)
- [x] 4.2 Documentar DDL completo da tabela `users`: `id UUID PK`, `cognito_sub VARCHAR UNIQUE`, `email`, `name`, `created_at`
- [x] 4.3 Documentar DDL completo da tabela `notebooks`: `id UUID PK`, `owner_id UUID FK users`, `name VARCHAR`, `description TEXT`, `created_at`, `updated_at`
- [x] 4.4 Documentar DDL completo da tabela `sources`: `id UUID PK`, `notebook_id UUID FK notebooks`, `name VARCHAR`, `type VARCHAR (FILE|URL)`, `s3_key TEXT`, `url TEXT`, `status VARCHAR (PENDING|PROCESSING|READY|FAILED)`, `error_message TEXT`, `created_at`
- [x] 4.5 Documentar DDL completo da tabela `source_chunks`: `id UUID PK`, `source_id UUID FK sources`, `content TEXT`, `embedding vector(1536)`, `chunk_index INT`, `embedding_model VARCHAR`, `created_at`; incluir índice `HNSW` no pgvector
- [x] 4.6 Documentar DDL completo da tabela `conversations`: `id UUID PK`, `notebook_id UUID FK notebooks`, `created_at`
- [x] 4.7 Documentar DDL da tabela `conversation_active_sources`: `conversation_id UUID FK`, `source_id UUID FK`, `PK (conversation_id, source_id)`
- [x] 4.8 Documentar DDL completo da tabela `conversation_messages`: `id UUID PK`, `conversation_id UUID FK`, `role VARCHAR (user|assistant)`, `content TEXT`, `created_at`

## 5. ARCHITECTURE.md — Contratos REST

- [x] 5.1 Documentar endpoints de notebooks: `GET /notebooks`, `POST /notebooks`, `GET /notebooks/{id}`, `PATCH /notebooks/{id}`, `DELETE /notebooks/{id}` — com request/response JSON e status codes
- [x] 5.2 Documentar endpoints de sources: `GET /notebooks/{id}/sources`, `POST /notebooks/{id}/sources` (multipart + JSON), `GET /notebooks/{id}/sources/{sourceId}`, `DELETE /notebooks/{id}/sources/{sourceId}` — com request/response JSON e status codes
- [x] 5.3 Documentar endpoints de chat/conversas: `GET /notebooks/{id}/conversations`, `POST /notebooks/{id}/conversations`, `GET /conversations/{id}/messages`, `POST /conversations/{id}/messages` (SSE) — com request/response JSON, status codes e formato do SSE stream
- [x] 5.4 Documentar headers comuns: `Authorization: Bearer <jwt>` (obrigatório em todos os endpoints protegidos); formato de erro padrão `{"error": "...", "message": "..."}` para 4xx/5xx
