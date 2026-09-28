# rds-postgres

Cria a instância RDS PostgreSQL. **Não conecta ao banco** — a extensão `vector`
é criada pelo Flyway (`V1__init_schema.sql`, `CREATE EXTENSION IF NOT EXISTS
vector`) usando o usuário master (`rds_superuser`), no primeiro boot da
aplicação. `V2` cria `hstore` da mesma forma.

`engine_version` é validado para major >= 16 (pgvector >= 0.5.0, necessário
para índices HNSW usados nas migrations). Sem `shared_preload_libraries`:
pgvector no RDS é extensão gerenciada, não exige preload.

A app deve usar o usuário master até existir uma role dedicada com
`CREATE EXTENSION` pré-executado — um usuário sem esse privilégio falha a
migration `V1`.
