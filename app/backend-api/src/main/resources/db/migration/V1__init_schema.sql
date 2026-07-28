-- Extensão pgvector (habilitar antes de rodar as migrations)
CREATE EXTENSION IF NOT EXISTS vector;

-- =========================================================
-- tb_users
-- =========================================================
CREATE TABLE tb_users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cognito_sub VARCHAR(256) NOT NULL UNIQUE,
    email       VARCHAR(320) NOT NULL,
    name        VARCHAR(256) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tb_users_cognito_sub ON tb_users (cognito_sub);

-- =========================================================
-- tb_notebooks
-- =========================================================
CREATE TABLE tb_notebooks (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL REFERENCES tb_users(id) ON DELETE CASCADE,
    name        VARCHAR(256) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tb_notebooks_owner_id ON tb_notebooks (owner_id);

-- =========================================================
-- tb_sources
-- =========================================================
CREATE TABLE tb_sources (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    notebook_id   UUID         NOT NULL REFERENCES tb_notebooks(id) ON DELETE CASCADE,
    name          VARCHAR(512) NOT NULL,
    type          VARCHAR(10)  NOT NULL CHECK (type IN ('FILE', 'URL')),
    s3_key        TEXT,                          -- preenchido para type = FILE
    url           TEXT,                          -- preenchido para type = URL
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                               CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    error_message TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tb_sources_notebook_id ON tb_sources (notebook_id);
CREATE INDEX idx_tb_sources_status      ON tb_sources (status);

-- =========================================================
-- tb_source_chunks
-- =========================================================
CREATE TABLE tb_source_chunks (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID          NOT NULL REFERENCES tb_sources(id) ON DELETE CASCADE,
    content         TEXT          NOT NULL,
    embedding       vector(1536)  NOT NULL,
    chunk_index     INTEGER       NOT NULL,
    embedding_model VARCHAR(128)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Índice HNSW para similarity search eficiente (cosine distance)
CREATE INDEX idx_tb_source_chunks_embedding_hnsw
    ON tb_source_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_tb_source_chunks_source_id ON tb_source_chunks (source_id);

-- =========================================================
-- tb_conversations
-- =========================================================
CREATE TABLE tb_conversations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    notebook_id UUID        NOT NULL REFERENCES tb_notebooks(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tb_conversations_notebook_id ON tb_conversations (notebook_id);

-- =========================================================
-- tb_conversation_active_sources  (M:N — conversations × sources)
-- =========================================================
CREATE TABLE tb_conversation_active_sources (
    conversation_id UUID NOT NULL REFERENCES tb_conversations(id) ON DELETE CASCADE,
    source_id       UUID NOT NULL REFERENCES tb_sources(id)       ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, source_id)
);

-- =========================================================
-- tb_conversation_messages
-- =========================================================
CREATE TABLE tb_conversation_messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES tb_conversations(id) ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL CHECK (role IN ('user', 'assistant')),
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tb_conv_messages_conversation_id ON tb_conversation_messages (conversation_id);
