-- App ainda em fase de teste: sem dado de produção a preservar.
-- Recria tb_users com email único (case-insensitive via normalização em código)
-- em vez de ALTER TABLE, evitando lidar com duplicatas existentes.
-- Notebooks (e tudo em cascata: sources, chunks, conversas) ficam órfãos sem dono válido.
TRUNCATE tb_notebooks CASCADE;

DROP TABLE tb_users CASCADE;

CREATE TABLE tb_users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    cognito_sub VARCHAR(256) NOT NULL UNIQUE,
    email       VARCHAR(320) NOT NULL UNIQUE,
    name        VARCHAR(256) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tb_users_cognito_sub ON tb_users (cognito_sub);

-- CASCADE acima só removeu a FK de tb_notebooks.owner_id (tabela em si não é dropada).
-- Restaura a FK apontando pra nova tb_users.
ALTER TABLE tb_notebooks
    ADD CONSTRAINT tb_notebooks_owner_id_fkey
    FOREIGN KEY (owner_id) REFERENCES tb_users(id) ON DELETE CASCADE;
