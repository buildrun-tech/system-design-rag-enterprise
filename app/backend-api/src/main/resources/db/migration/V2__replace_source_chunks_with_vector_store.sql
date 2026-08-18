-- Extensão adicional usada pelo Spring AI PgVectorStore
CREATE EXTENSION IF NOT EXISTS hstore;

-- =========================================================
-- vector_store (schema gerenciado pelo Spring AI PgVectorStore;
-- metadata JSON carrega source_id/chunk_index/embedding_model).
-- Formato e nome de tabela seguem o default de PgVectorStore —
-- initialize-schema fica desligado, essa migration é a fonte da
-- verdade. Dimensão 1536 (truncada via spring.ai.openai.embedding.options.dimensions):
-- pgvector HNSW nao indexa acima de 2000 dims, e text-embedding-3-large
-- nativo sai em 3072.
-- =========================================================
DROP TABLE tb_source_chunks;

CREATE TABLE vector_store (
    id        UUID         PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding vector(1536)
);

CREATE INDEX spring_ai_vector_index
    ON vector_store
    USING hnsw (embedding vector_cosine_ops);
