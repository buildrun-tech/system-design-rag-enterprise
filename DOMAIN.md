# Domain — system-design-rag-enterprise

Entidades do domínio, relacionamentos, regras de negócio e schema do banco de dados.
Documentos relacionados: [ARCHITECTURE.md](ARCHITECTURE.md) · [API.md](API.md)

---

## 1. Entidades e Regras de Negócio

### User
Usuário autenticado via AWS Cognito. Criado automaticamente no primeiro acesso com um JWT válido.

**Atributos:**
- `id` — UUID interno do sistema
- `cognito_sub` — identificador único do Cognito; chave de lookup para todo request autenticado
- `email`, `name` — extraídos do JWT no momento da criação

**Regras:**
- O sistema NUNCA aceita `userId` como parâmetro de request — ele é sempre extraído do JWT via `SecurityContextHolder`
- Se o `cognito_sub` não existir na tabela `users` ao receber um JWT válido, o registro é criado automaticamente (upsert no primeiro acesso)

---

### Notebook
Unidade lógica de agrupamento de sources. Pertence exclusivamente a um usuário.

**Atributos:**
- `id` — UUID
- `owner_id` — FK para `users`
- `name` — obrigatório, 1–256 caracteres
- `description` — opcional, até 2048 caracteres
- `created_at`, `updated_at`

**Regras:**
- Um usuário pode ter múltiplos notebooks
- Somente o `owner_id` pode ler, editar, ou deletar o notebook
- Requisições de outro usuário a um notebook alheio retornam `404 Not Found` (não revelar existência)
- Deleção de notebook CASCADE: remove sources → source_chunks → conversations → conversation_messages e arquivos do S3

---

### Source
Documento ou URL adicionada a um notebook como base de conhecimento para o RAG.

**Atributos:**
- `id` — UUID
- `notebook_id` — FK para `notebooks`
- `name` — nome exibido ao usuário
- `type` — `FILE` ou `URL`
- `s3_key` — caminho no S3 (preenchido apenas para `type = FILE`); formato: `{userId}/{notebookId}/{sourceId}/{filename}`
- `url` — URL original (preenchida apenas para `type = URL`)
- `status` — ciclo de vida: `PENDING → PROCESSING → READY | FAILED`
- `error_message` — preenchido apenas quando `status = FAILED`

**Formatos suportados (FILE):** PDF, DOCX, Markdown (`.md`)

**Ciclo de vida:**
```
PENDING     → source criada, aguardando consumo da fila SQS
PROCESSING  → consumer SQS iniciou extração/chunking/embedding
READY       → chunks e vetores persistidos; source disponível para RAG
FAILED      → erro irrecuperável após retentativas; error_message preenchido
```

**Regras:**
- Uma source só é elegível para RAG quando `status = READY`
- Em caso de falha, a mensagem SQS vai para a DLQ após esgotar retentativas; `status` é atualizado para `FAILED`
- Deleção de source CASCADE: remove `source_chunks` do pgvector e o arquivo do S3

---

### SourceChunk
Fragmento de texto de uma source, com seu vetor de embedding associado.

**Atributos:**
- `id` — UUID
- `source_id` — FK para `sources`
- `content` — texto do chunk (~512 tokens)
- `embedding` — vetor `vector(1536)` gerado pelo modelo de embedding
- `chunk_index` — posição do chunk dentro da source (0-based)
- `embedding_model` — nome do modelo usado (ex: `text-embedding-ada-002`); necessário para detectar incompatibilidade se o modelo mudar
- `created_at`

**Regras:**
- Chunks são gerados com tamanho de ~512 tokens e overlap de ~50 tokens entre chunks consecutivos
- A dimensão do vetor (1536) é acoplada ao modelo de embedding; trocar de modelo invalida vetores existentes
- O campo `embedding_model` deve ser verificado antes de qualquer migração de modelo

---

### Conversation
Sessão de chat dentro de um notebook. Registra quais sources estão ativas para o RAG nessa conversa.

**Atributos:**
- `id` — UUID
- `notebook_id` — FK para `notebooks`
- `created_at`
- `activeSourceIds` — lista de sources ativas (tabela `conversation_active_sources`)

**Regras:**
- Se criada sem seleção explícita de sources, todas as sources com `status = READY` do notebook ficam ativas
- Sources adicionadas ao notebook após a criação da conversa NÃO são incluídas automaticamente
- Somente o dono do notebook pode acessar as conversas

---

### ConversationMessage
Mensagem individual dentro de uma conversa — enviada pelo usuário ou gerada pelo assistente.

**Atributos:**
- `id` — UUID
- `conversation_id` — FK para `conversations`
- `role` — `user` ou `assistant`
- `content` — texto completo da mensagem
- `created_at`

**Regras:**
- Mensagens do usuário são persistidas imediatamente ao receber o request
- Mensagens do assistente são persistidas após o SSE stream completar (resposta completa)
- As últimas 10 mensagens da conversa são incluídas no prompt como histórico
- Mensagens são ordenadas por `created_at ASC` para montar o histórico corretamente

---

## 2. Modelo de Relacionamentos (ERD)

```
┌──────────────┐        ┌────────────────────┐        ┌──────────────────┐
│    users     │ 1    N │     notebooks      │ 1    N │     sources      │
│──────────────│────────│────────────────────│────────│──────────────────│
│ id (PK)      │        │ id (PK)            │        │ id (PK)          │
│ cognito_sub  │        │ owner_id (FK)      │        │ notebook_id (FK) │
│ email        │        │ name               │        │ name             │
│ name         │        │ description        │        │ type             │
│ created_at   │        │ created_at         │        │ s3_key           │
└──────────────┘        │ updated_at         │        │ url              │
                        └────────────────────┘        │ status           │
                                 │                    │ error_message    │
                                 │ 1                  │ created_at       │
                                 │                    └──────────────────┘
                                 │                             │ 1
                                 │                             │ N
                                 │                    ┌──────────────────────┐
                                 │                    │    source_chunks     │
                                 │                    │──────────────────────│
                                 │                    │ id (PK)              │
                                 │                    │ source_id (FK)       │
                                 │                    │ content              │
                                 │                    │ embedding vector(1536)│
                                 │                    │ chunk_index          │
                                 │                    │ embedding_model      │
                                 │                    │ created_at           │
                                 │                    └──────────────────────┘
                                 │ 1
                                 │ N
                        ┌────────────────────┐
                        │   conversations    │
                        │────────────────────│
                        │ id (PK)            │
                        │ notebook_id (FK)   │
                        │ created_at         │
                        └────────────────────┘
                                 │
                    ┌────────────┴──────────────────┐
                    │ N                             │ 1
                    │                              N│
          ┌──────────────────────┐    ┌──────────────────────────────┐
          │ conv_active_sources  │    │   conversation_messages      │
          │──────────────────────│    │──────────────────────────────│
          │ conversation_id (FK) │    │ id (PK)                      │
          │ source_id (FK)       │    │ conversation_id (FK)         │
          │ PK (conv_id, src_id) │    │ role (user|assistant)        │
          └──────────────────────┘    │ content                      │
             M:N entre conversations  │ created_at                   │
             e sources                └──────────────────────────────┘
```

---

## 3. Schema do Banco de Dados (DDL)

```sql
-- Extensão pgvector (habilitar antes de rodar as migrations)
CREATE EXTENSION IF NOT EXISTS vector;

-- =========================================================
-- users
-- =========================================================
CREATE TABLE users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cognito_sub VARCHAR(256) NOT NULL UNIQUE,
    email       VARCHAR(320) NOT NULL,
    name        VARCHAR(256) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_cognito_sub ON users (cognito_sub);

-- =========================================================
-- notebooks
-- =========================================================
CREATE TABLE notebooks (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(256) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notebooks_owner_id ON notebooks (owner_id);

-- =========================================================
-- sources
-- =========================================================
CREATE TABLE sources (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    notebook_id   UUID         NOT NULL REFERENCES notebooks(id) ON DELETE CASCADE,
    name          VARCHAR(512) NOT NULL,
    type          VARCHAR(10)  NOT NULL CHECK (type IN ('FILE', 'URL')),
    s3_key        TEXT,                          -- preenchido para type = FILE
    url           TEXT,                          -- preenchido para type = URL
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                               CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sources_notebook_id ON sources (notebook_id);
CREATE INDEX idx_sources_status      ON sources (status);

-- =========================================================
-- source_chunks
-- =========================================================
CREATE TABLE source_chunks (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID          NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    content         TEXT          NOT NULL,
    embedding       vector(1536)  NOT NULL,
    chunk_index     INTEGER       NOT NULL,
    embedding_model VARCHAR(128)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Índice HNSW para similarity search eficiente (cosine distance)
CREATE INDEX idx_source_chunks_embedding_hnsw
    ON source_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_source_chunks_source_id ON source_chunks (source_id);

-- =========================================================
-- conversations
-- =========================================================
CREATE TABLE conversations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    notebook_id UUID        NOT NULL REFERENCES notebooks(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_notebook_id ON conversations (notebook_id);

-- =========================================================
-- conversation_active_sources  (M:N — conversations × sources)
-- =========================================================
CREATE TABLE conversation_active_sources (
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    source_id       UUID NOT NULL REFERENCES sources(id)       ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, source_id)
);

-- =========================================================
-- conversation_messages
-- =========================================================
CREATE TABLE conversation_messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL CHECK (role IN ('user', 'assistant')),
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_conv_messages_conversation_id ON conversation_messages (conversation_id);
```
