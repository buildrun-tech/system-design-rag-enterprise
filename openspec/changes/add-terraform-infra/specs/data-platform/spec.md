## ADDED Requirements

### Requirement: RDS PostgreSQL com pgvector
O módulo `rds-postgres` SHALL criar uma instância RDS PostgreSQL de versão major ≥ 16 cuja engine suporte pgvector ≥ 0.5.0 (necessário para índices HNSW usados em `V1__init_schema.sql` e `V2__replace_source_chunks_with_vector_store.sql`). O módulo SHALL validar via `variable validation` que `engine_version` é major ≥ 16. A instância SHALL usar `publicly_accessible = false`, subnet group nas subnets `db`, storage criptografado e SG próprio sem regras. O módulo SHALL NOT usar `manage_master_user_password`.

#### Scenario: Engine sem suporte é rejeitada
- **WHEN** `engine_version` é definida com major menor que 16
- **THEN** `terraform validate`/`plan` falha com mensagem indicando o requisito de pgvector

#### Scenario: Instância privada
- **WHEN** a instância é criada
- **THEN** `publicly_accessible = false` e o subnet group contém somente subnets `db`

#### Scenario: Senha vem de fora do módulo
- **WHEN** o módulo é instanciado
- **THEN** recebe `master_username` e `master_password` (sensível) como inputs e não cria secret próprio

### Requirement: Extensão vector habilitada no banco
A extensão `vector` SHALL estar habilitada no database `notebooklm` antes de qualquer migration que use o tipo `vector`. A habilitação SHALL ocorrer pelas migrations Flyway existentes (`CREATE EXTENSION IF NOT EXISTS vector` em `V1`, `hstore` em `V2`) executadas com o usuário master (`rds_superuser`). O módulo SHALL NOT exigir conexão do Terraform ao banco, e SHALL NOT exigir `shared_preload_libraries` para pgvector. O módulo SHALL criar um parameter group da família da engine para futura configuração.

#### Scenario: Primeiro boot da app cria a extensão
- **WHEN** a primeira task ECS sobe contra o RDS recém-criado com o usuário master
- **THEN** o Flyway executa `V1` sem erro e `SELECT extname FROM pg_extension` retorna `vector` e `hstore`

#### Scenario: Migration com HNSW
- **WHEN** `V1` e `V2` criam índices `USING hnsw (embedding vector_cosine_ops)`
- **THEN** ambos são criados sem erro na versão de engine provisionada

#### Scenario: Usuário sem privilégio
- **WHEN** a app usa um usuário que não é `rds_superuser` nem dono com privilégio de criar extensão
- **THEN** a migration falha; por isso a app SHALL usar o usuário master até existir role dedicada com `CREATE EXTENSION` prévio

### Requirement: Buckets S3 via módulo genérico
O módulo `s3-bucket` SHALL criar um bucket com block public access total, SSE (AES256), ownership `BucketOwnerEnforced` e policy que nega (`Deny`) requisições com `aws:SecureTransport = false`. SHALL expor `force_destroy`, `versioning` e `cors_rules` como variáveis opcionais. O módulo SHALL NOT anexar policy de acesso de leitura/escrita; isso é responsabilidade do consumidor.

#### Scenario: Bucket privado
- **WHEN** qualquer bucket criado pelo módulo é inspecionado
- **THEN** todas as quatro opções de block public access estão `true`

#### Scenario: HTTP puro negado
- **WHEN** um cliente acessa o bucket sem TLS
- **THEN** a requisição é negada pela policy

### Requirement: Dois buckets separados
O ambiente SHALL ter exatamente dois buckets: `rag-<env>-sources` (raw sources do backend; sem CORS; acesso só pela task role) e `rag-<env>-frontend` (estáticos; acesso só pelo CloudFront via OAC). Os nomes SHALL incluir o account id ou sufixo aleatório para unicidade global.

#### Scenario: Sources sem acesso público
- **WHEN** um cliente anônimo requisita um objeto do bucket `sources`
- **THEN** recebe `403`

#### Scenario: Frontend só via CloudFront
- **WHEN** um cliente anônimo requisita um objeto direto no endpoint S3 do bucket `frontend`
- **THEN** recebe `403`, e a mesma chave via domínio CloudFront retorna `200`

### Requirement: Fila SQS com DLQ
O módulo `sqs-queue` SHALL criar uma fila principal e uma DLQ com redrive (`maxReceiveCount` configurável, padrão 5), SSE gerenciado, `visibility_timeout_seconds` configurável e retenção da DLQ maior que a da fila principal. Deve exportar `queue_url`, `queue_arn`, `dlq_arn`.

#### Scenario: Mensagem venenosa
- **WHEN** uma mensagem é recebida mais de `maxReceiveCount` vezes sem ser deletada
- **THEN** ela é movida para a DLQ

### Requirement: Secret único da aplicação
O módulo `secret` SHALL criar exatamente um `aws_secretsmanager_secret` cujo valor é um JSON. O ambiente SHALL ter um único secret `rag/<env>/app` com as chaves `username`, `password`, `host`, `port`, `dbname` e `openrouter_api_key`. `password` SHALL ser gerada por `random_password` (sem caracteres que quebrem URL/JDBC) na raiz e ser a mesma passada ao RDS. `openrouter_api_key` SHALL entrar por variável sensível (`TF_VAR_openrouter_api_key`). `recovery_window_in_days` SHALL ser variável.

#### Scenario: Um secret só
- **WHEN** os recursos `aws_secretsmanager_secret` do plan são contados
- **THEN** existe exatamente um por ambiente

#### Scenario: Senha consistente
- **WHEN** o apply termina
- **THEN** o campo `password` do secret é idêntico à senha do usuário master do RDS

#### Scenario: Chave OpenRouter ausente
- **WHEN** `openrouter_api_key` não é informada
- **THEN** o plan falha (variável sem default)

#### Scenario: Valor não vaza em output
- **WHEN** `terraform output` é executado
- **THEN** nenhum campo do JSON do secret aparece
