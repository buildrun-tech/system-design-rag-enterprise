# rds-postgres

Cria a instância RDS PostgreSQL. **Não conecta ao banco** — a extensão `vector`
é criada pelo Flyway (`V1__init_schema.sql`, `CREATE EXTENSION IF NOT EXISTS
vector`) usando o usuário master (`rds_superuser`), no primeiro boot da
aplicação. `V2` cria `hstore` da mesma forma.

`engine_version` é validado para major >= 16 (pgvector >= 0.5.0, necessário
para índices HNSW usados nas migrations). Sem `shared_preload_libraries`:
pgvector no RDS é extensão gerenciada, não exige preload. Qualquer minor de
PG >= 16 disponível na AWS já traz pgvector >= 0.5.0 no allowlist de
extensões — nada a configurar além da versão da engine.

O módulo aceita `engine_version` como major only (ex: `"18"`, igual ao
`pgvector/pgvector:pg18` usado localmente) ou major.minor. `data
"aws_rds_engine_version" "this"` resolve a minor real disponível na região
(`version_actual`) e a family do parameter group correspondente, evitando o
erro `Cannot find version X.Y for postgres` quando uma minor pinada é
descontinuada pela AWS. Trade-off: com major only, a minor resolvida pode
mudar entre applies se a AWS lançar uma nova (Terraform propõe upgrade
in-place da engine, não recria a instância) — pra travar numa minor exata,
passe `engine_version = "18.1"` (ou a que for) em vez de só o major.

A app deve usar o usuário master até existir uma role dedicada com
`CREATE EXTENSION` pré-executado — um usuário sem esse privilégio falha a
migration `V1`.
