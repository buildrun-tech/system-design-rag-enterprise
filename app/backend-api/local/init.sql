-- Extensão pgvector precisa existir antes das migrations Flyway rodarem.
-- Schema (tabelas, índices) é gerenciado pelo Flyway — ver src/main/resources/db/migration/
CREATE EXTENSION IF NOT EXISTS vector;
