## Why

`infra/` é esqueleto vazio (`main.tf`, `variables.tf`, `outputs.tf` com 0 bytes; `envs/{dev,prod}/terraform.tfvars` só com comentário). A pipeline da change `add-cicd-github-actions` já faz deploy de ECS e S3/CloudFront, mas aponta pra recursos que não existem e lê nomes de `vars.*` preenchidas à mão. Sem Terraform, a solução do diagrama (`docs/solution.drawio`) não sobe, e o deploy da app não tem onde rodar.

Esta change escreve a infra base em módulos pequenos, auto-contidos e reutilizáveis, com foco em: (1) custo baixo — sem NAT gateway, sem ACM/Route53; (2) isolamento de rede garantido por security groups encadeados a partir do API Gateway; (3) IAM de menor privilégio com trusts explícitos; (4) contrato de outputs estável pro CI.

## What Changes

- Cria 11 módulos Terraform em `infra/modules/`: `network`, `s3-bucket`, `sqs-queue`, `secret`, `rds-postgres`, `cognito-user-pool`, `alb`, `ecs-cluster`, `ecs-service`, `api-gateway-http`, `cloudfront-spa`. Cada um com `main.tf`, `variables.tf`, `outputs.tf`, `versions.tf`.
- Raiz única em `infra/` (`main.tf` só faz wiring de módulos) + `envs/{dev,prod}/{terraform.tfvars,backend.hcl}`. Um state por ambiente (S3, `use_lockfile`). Região `us-east-2`.
- **Rede sem NAT:** subnets públicas (ALB, VPC Link, tasks ECS com public IP) + subnets `db` isoladas (sem rota pra IGW) em 2 AZs. Nenhum NAT gateway, nenhum VPC endpoint.
- **Cadeia de security groups** `vpclink-sg → alb-sg → task-sg → db-sg`. SGs nascem vazios no módulo dono; **quem consome cria as regras** (evita ciclo de dependência). ALB é `internal`, então única entrada da internet pra app é API Gateway → VPC Link.
- **Dois buckets S3** (mesmo módulo `s3-bucket`): `sources` (raw sources do backend, acesso só via IAM da task role) e `frontend` (estáticos, acesso só via CloudFront OAC).
- **`rds-postgres` com pgvector:** PostgreSQL 16+ (versão com pgvector ≥ 0.5 pra HNSW), `publicly_accessible = false`, subnet group nas subnets `db`. Extensão `vector` habilitada pelas migrations Flyway já existentes (`V1` cria `vector`, `V2` cria `hstore`) executadas com o usuário master (`rds_superuser`); módulo garante engine compatível e pré-condição verificável.
- **Secret único** no Secrets Manager (`rag/<env>/app`, JSON) com credenciais do RDS (`username`, `password`, `host`, `port`, `dbname`) + `openrouter_api_key`. Senha do RDS gerada por `random_password` (sem `manage_master_user_password`, que criaria 2º secret).
- **IAM:** roles `rag-<env>-exec` e `rag-<env>-task` com nomes determinísticos, trust `ecs-tasks.amazonaws.com` + `aws:SourceAccount`/`aws:SourceArn`. Exec role lê só o ARN do secret único; task role acessa só bucket `sources` e ingest-queue. CloudFront→S3 via OAC (bucket policy com `AWS:SourceArn`). Requisitos de permissão da role OIDC do GitHub (manual) documentados, incluindo `iam:PassRole` restrito.
- **SQS** `ingest-queue` + DLQ com redrive.
- **Cognito** user pool + app client + domain prefix (domínio padrão `amazoncognito.com`); signup direto habilitado por variável, IdP Google opcional. Sem GitHub IdP (Cognito não suporta nativo).
- **API Gateway HTTP API** com VPC Link privado pro ALB, rota `ANY /{proxy+}`, CORS restrito ao domínio CloudFront.
- **CloudFront** com domínio padrão `*.cloudfront.net`, fallback SPA (403/404 → `index.html`).
- **ECS service** criado pelo Terraform com task definition **bootstrap** e `lifecycle.ignore_changes = [task_definition, desired_count]`; primeiro apply com `desired_count = 0`. CI passa a clonar a última revisão da família (`describe-task-definition`), trocar só `image` e registrar — env e secrets ficam no Terraform, imagem no CI.
- **Outputs como contrato do CI:** `ecs_cluster_name`, `ecs_service_name`, `ecs_task_family`, `ecs_exec_role_arn`, `ecs_task_role_arn`, `frontend_bucket_name`, `cloudfront_distribution_id`, `api_url`, `cognito_authority`, `cognito_client_id`, `cognito_user_pool_id`. Substituem as repository variables manuais.
- Documenta pré-requisitos no app (fora do escopo de código desta change): profile `prod`, endpoint de health sem JWT, remoção dos defaults `test`/`localhost:4566`.

## Capabilities

### New Capabilities
- `terraform-foundation`: layout da raiz `infra/`, ambientes dev/prod, backend de state, providers/versões, gate de destroy, convenção de nomes e contrato de outputs consumido pelo CI.
- `network-security`: VPC, subnets públicas e `db` isoladas, ausência de NAT, e a cadeia de security groups entre VPC Link, ALB, tasks e RDS com regras criadas pelo consumidor.
- `data-platform`: RDS PostgreSQL com pgvector, buckets S3 (`sources` e `frontend`), fila SQS + DLQ e secret único no Secrets Manager.
- `compute-runtime`: ALB interno, cluster ECS, ECS service (task definition bootstrap), roles exec/task e seus trusts/permissões.
- `edge-delivery`: API Gateway HTTP API + VPC Link, CloudFront + OAC, Cognito user pool, CORS e cadeia de origem sem domínio customizado.

### Modified Capabilities
(nenhuma — `openspec/specs/` não tem spec de infra. A change `add-cicd-github-actions` (ainda não arquivada) é ajustada via nota de dependência, ver Impact.)

## Impact

- **Novos arquivos:** `infra/main.tf`, `infra/providers.tf`, `infra/backend.tf`, `infra/versions.tf`, `infra/variables.tf`, `infra/outputs.tf`, `infra/envs/{dev,prod}/{terraform.tfvars,backend.hcl}`, `infra/modules/*` (11 módulos).
- **Fora do Terraform (pré-existentes/manuais):** role OIDC `ghactions-rag-enterprise`, ECRs `develop`/`production`, bucket de state (bootstrap manual). Conta `069765036136`, região `us-east-2`.
- **Custo/segurança aceitos:** egress 443 aberto nas tasks (sem NAT/endpoints); tasks com public IP mas ingress só do `alb-sg`; senha do RDS e `openrouter_api_key` presentes no state (bucket de state precisa de SSE e acesso restrito); role OIDC única cobre dev e prod.
- **`add-cicd-github-actions`:** os deploy jobs devem ler outputs do Terraform em vez de `vars.*` e usar clone de task definition em vez de template próprio; ajuste feito na implementação desta change ou em follow-up. O job `infra.yml` passa a validar `infra/` de verdade (`fmt -check`, `validate`).
- **Backend (`app/backend-api`):** sem mudança de código nesta change. Pré-requisitos listados em design.md (profile `prod`, health endpoint, defaults AWS) bloqueiam o funcionamento em ECS, mas são tratados em change separada.
- **`docs/solution.drawio`:** box `dns` deixa de existir (domínios padrão).
- **Local dev:** `app/local/docker-compose.yml` usa `pgvector/pgvector:pg18`; RDS será PG16+ — alinhar versão é recomendação, não requisito.
