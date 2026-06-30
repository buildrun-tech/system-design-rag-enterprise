## Context

O sistema é um NotebookLM empresarial simplificado — uma plataforma RAG onde usuários organizam documentos em "notebooks" e conversam com eles via IA. O projeto parte do zero: não há código de produção existente, apenas o scaffolding do repositório.

Stack definida: Spring Boot + Spring AI (Java), AWS (Cognito, S3, RDS PostgreSQL + pgvector, API Gateway, ALB, ECS), React (SPA), LLM via provider OpenAI-compatible (Bedrock ou OpenRouter).

Esta mudança é exclusivamente de documentação e configuração de harness — sem código de produção.

## Goals / Non-Goals

**Goals:**
- Estabelecer `CLAUDE.md` com convenções, stack e estrutura de módulos do projeto
- Configurar `openspec/config.yaml` com contexto do projeto para geração de artefatos futuros
- Criar `ARCHITECTURE.md` com:
  - Diagrama de infraestrutura AWS (ASCII)
  - ERD das entidades do domínio
  - DDL das tabelas PostgreSQL (incluindo extensão pgvector)
  - Contratos REST completos (URL, método HTTP, request/response JSON, status codes)
  - Fluxos de dados dos casos de uso principais

**Non-Goals:**
- Implementação de qualquer código de produção (backend, frontend, infra-as-code)
- Configuração de CI/CD, Terraform, ou ambientes AWS
- Decisões de deployment (ECS vs EKS, VPC layout, etc.)

## Decisions

### Decisão 1: Estrutura de módulos Spring Boot

**Escolha:** Estrutura modular por domínio (package-by-feature), não por camada técnica.

```
com.enterprise.rag/
  auth/          # Cognito JWT filter, SecurityConfig
  notebook/      # Notebook entity, service, controller
  source/        # Source entity, S3 upload, SQS publisher
  ingestion/     # SQS consumer, text extraction, chunking, embedding
  chat/          # RAG retrieval, LLM call, SSE streaming
  shared/        # Common DTOs, exceptions, config
```

**Por quê:** Facilita navegação por feature, coesão maior dentro de cada módulo, e alinha com como o Spring AI organiza seus componentes (VectorStore, ChatModel são configurados por feature).

**Alternativa descartada:** Package-by-layer (controllers/, services/, repositories/) — dificulta entender o escopo de uma feature e torna refatorações entre camadas mais trabalhosas.

### Decisão 2: Processamento assíncrono via SQS (Async Request-Reply)

**Escolha:** Upload → S3 → SQS → consumer worker (mesmo app, listener separado) → status via polling ou callback.

```
POST /sources          → salva metadado no DB (status: PENDING)
                       → faz upload do arquivo para S3
                       → publica mensagem SQS com sourceId
                       → retorna 202 Accepted + sourceId

[SQS Consumer - async]
  → lê mensagem SQS
  → baixa arquivo do S3
  → extrai texto (Tika para PDF/DOCX, fetch para URL)
  → chunking (tamanho fixo com overlap)
  → embedding via Spring AI EmbeddingModel
  → persiste chunks + vetores em pgvector
  → atualiza source.status = READY

GET /sources/{id}/status → retorna status atual (PENDING | PROCESSING | READY | FAILED)
```

**Por quê:** Mantém o app stateless (SQS carrega o estado da fila), desacopla latência de extração/embedding do request HTTP, permite retry automático via SQS.

**Alternativa descartada:** Processamento síncrono no upload — bloquearia o request por até 30-60s para documentos grandes e quebraria com load balancer timeouts.

### Decisão 3: SSE para streaming de chat

**Escolha:** Spring `SseEmitter` no controller de chat, sem WebSocket.

**Por quê:** SSE é unidirecional (server → client), que é exatamente o padrão de streaming de tokens LLM. Simples de implementar com Spring AI's `ChatModel.stream()`. O API Gateway HTTP API suporta chunked transfer encoding necessário para SSE.

**Cuidado:** API Gateway REST API tem timeout de 29s — usar HTTP API do API Gateway que suporta streaming nativo e não tem esse limitante da mesma forma.

**Alternativa descartada:** WebSocket — overhead desnecessário para o padrão unidirecional de streaming de tokens.

### Decisão 4: Autenticação stateless via JWT Cognito

**Escolha:** O app valida o JWT emitido pelo Cognito em cada request. Sem sessão server-side.

```
Browser → Cognito Hosted UI (OAuth2 PKCE) → JWT (access_token + id_token)
Browser → API com Authorization: Bearer <access_token>
Spring Security Filter → valida JWT via Cognito JWKS endpoint → extrai userId (sub)
```

**Por quê:** Alinha com o requisito de statelessness. JWT carrega `sub` (userId) e scopes; não é necessário consultar banco para autenticar cada request.

### Decisão 5: pgvector para embeddings

**Escolha:** Tabela `source_chunks` com coluna `embedding vector(1536)` — dimensão compatível com modelos de embedding comuns (OpenAI text-embedding-ada-002, Bedrock Titan Embeddings).

**Por quê:** Spring AI tem `PgVectorStore` nativo. Consulta de similarity com filtro por `notebook_id` e lista de `source_id` ativos é trivial com `WHERE` clause + operador `<=>` (cosine distance).

## Risks / Trade-offs

**[Risco] API Gateway timeout para SSE** → Usar HTTP API (não REST API) do API Gateway, que suporta streaming sem o hard limit de 29s da integração Lambda/HTTP.

**[Risco] Custo de embedding no upload** → Embedding é chamado para cada chunk de cada documento. Para documentos grandes, isso gera muitas chamadas ao LLM provider. Mitigação: chunking com tamanho razoável (512 tokens) e considerar batching.

**[Risco] Dimensão do vetor acoplada ao modelo de embedding** → Se trocar de modelo de embedding, os vetores existentes ficam inválidos. Mitigação: registrar o modelo usado por chunk na tabela (`embedding_model` column), e documentar que migração requer re-embedding.

**[Trade-off] SQS vs processamento síncrono** → SQS adiciona latência entre upload e disponibilidade da source para chat (fonte não fica disponível imediatamente). Contraparte: robustez e statelessness são mantidos.
