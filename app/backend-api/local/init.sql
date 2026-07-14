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
