## 1. Fundação da raiz Terraform

- [x] 1.1 Criar `infra/versions.tf` (`required_version >= 1.10`, provider `hashicorp/aws ~> 5`, `hashicorp/random`) e `infra/providers.tf` (região `us-east-2`, `default_tags` Project/Environment/ManagedBy)
- [x] 1.2 Criar `infra/backend.tf` (`backend "s3" {}` parcial, `use_lockfile = true`, `encrypt = true`) e `infra/envs/{dev,prod}/backend.hcl` (bucket, key por ambiente, region)
- [x] 1.3 Declarar `infra/variables.tf`: `environment`, `project`, `vpc_cidr`, `openrouter_api_key` (sensitive, sem default), tamanhos (RDS class, task cpu/mem, `desired_count`), flags de proteção (`deletion_protection`, `skip_final_snapshot`, `force_destroy`, `secret_recovery_window_days`), `cognito_allow_signup`, `google_client_id`/`google_client_secret` opcionais
- [x] 1.4 Preencher `infra/envs/dev/terraform.tfvars` e `infra/envs/prod/terraform.tfvars` (dev descartável, prod protegido, `desired_count = 0` no primeiro apply)
- [x] 1.5 Criar `infra/README.md`: bootstrap do bucket de state, init por ambiente, permissões exigidas da role OIDC (incluindo `iam:PassRole` restrito), fluxo de destroy via `destroy_config.json`
- [x] 1.6 Verificar `terraform fmt -check -recursive infra` e `terraform init -backend=false && terraform validate` na raiz

## 2. Módulo `network`

- [x] 2.1 Criar `modules/network`: VPC, 2 subnets públicas e 2 subnets `db` em 2 AZs (`cidrsubnet`), IGW, route table pública (0.0.0.0/0→IGW), route table `db` só com rota local
- [x] 2.2 Outputs: `vpc_id`, `vpc_cidr`, `public_subnet_ids`, `db_subnet_ids`
- [x] 2.3 Confirmar ausência de `aws_nat_gateway`, EIP de NAT e `aws_vpc_endpoint`; `terraform validate` isolado do módulo

## 3. Módulos genéricos de armazenamento e mensageria

- [x] 3.1 Criar `modules/s3-bucket`: bucket, block public access total, SSE AES256, `BucketOwnerEnforced`, policy `Deny` non-TLS, variáveis `force_destroy`, `versioning`, `cors_rules`; outputs `bucket_name`, `bucket_arn`, `bucket_regional_domain_name`
- [x] 3.2 Criar `modules/sqs-queue`: fila + DLQ, redrive (`max_receive_count` padrão 5), SSE, `visibility_timeout_seconds`, retenção DLQ maior; outputs `queue_url`, `queue_arn`, `dlq_arn`
- [x] 3.3 Criar `modules/secret`: um `aws_secretsmanager_secret` + `aws_secretsmanager_secret_version` com `secret_string` sensível (JSON), `recovery_window_in_days` variável; outputs `secret_arn` (sem valor)

## 4. Módulo `rds-postgres` (com pgvector)

- [x] 4.1 Criar `modules/rds-postgres`: `aws_db_subnet_group` (subnets `db`), `aws_security_group` sem regras inline, parameter group da família da engine, `aws_db_instance` (`publicly_accessible = false`, `storage_encrypted = true`, `db_name = notebooklm`, sem `manage_master_user_password`)
- [x] 4.2 Adicionar `variable validation` em `engine_version` exigindo major ≥ 16 (pgvector ≥ 0.5.0 / HNSW) com mensagem citando pgvector
- [x] 4.3 Inputs `master_username`, `master_password` (sensitive), `instance_class`, `allocated_storage`, `multi_az`, `deletion_protection`, `skip_final_snapshot`; outputs `endpoint` (address), `port`, `db_name`, `sg_id`
- [ ] 4.4 Confirmar versão exata do PG na região (`aws rds describe-db-engine-versions --engine postgres`) e a versão de pgvector correspondente; registrar no `variables.tf` como default
- [x] 4.5 Documentar no `README` do módulo que a extensão `vector` é criada pelo Flyway (`V1`) com o usuário master, sem conexão do Terraform ao banco

## 5. Módulo `cognito-user-pool`

- [x] 5.1 Criar `modules/cognito-user-pool`: user pool, app client público (`ALLOW_USER_PASSWORD_AUTH`, `ALLOW_REFRESH_TOKEN_AUTH`), domain prefix, `callback_urls`/`logout_urls` por variável, `allow_admin_create_user_only` por variável
- [x] 5.2 IdP Google condicional (`count` por presença de credenciais); sem GitHub
- [x] 5.3 Outputs: `user_pool_id`, `client_id`, `issuer_url`, `domain`

## 6. Módulos `alb` e `ecs-cluster`

- [x] 6.1 Criar `modules/alb`: ALB `internal = true` em subnets públicas, `aws_security_group` sem regras, listener HTTP :80 com resposta fixa 404; outputs `alb_arn`, `alb_sg_id`, `listener_arn`
- [x] 6.2 Criar `modules/ecs-cluster`: cluster, Container Insights por variável, capacity provider FARGATE; outputs `cluster_arn`, `cluster_name`

## 7. Módulo `ecs-service`

- [x] 7.1 Criar recursos base: log group (retenção por variável), `aws_security_group` `task-sg` sem regras inline, target group `ip` com health check configurável, listener rule (path/prioridade) no `listener_arn`
- [x] 7.2 Criar roles `rag-<env>-exec` e `rag-<env>-task` com trust `ecs-tasks.amazonaws.com` + `aws:SourceAccount` e `ArnLike aws:SourceArn`; exec com `AmazonECSTaskExecutionRolePolicy` + `secretsmanager:GetSecretValue` só no ARN do secret; task com policy JSON recebida por variável (`task_policy_json`)
- [x] 7.3 Criar task definition bootstrap Fargate com `environment` (URL JDBC, região, bucket, fila, Cognito, CORS, `SPRING_AI_OPENAI_*` não secretas) e `secrets` (`valueFrom = "<arn>:username::"`, `password`, `openrouter_api_key`); imagem placeholder por variável
- [x] 7.4 Criar `aws_ecs_service` (Fargate, subnets públicas, `assign_public_ip = true`, circuit breaker com rollback, `lifecycle.ignore_changes = [task_definition, desired_count]`, `desired_count` inicial 0)
- [x] 7.5 Criar regras de SG do consumidor: ingress em `task-sg` ← `alb-sg` (porta do container); egress em `alb-sg` → `task-sg`; egress `task-sg` → `db-sg` 5432; ingress `db-sg` ← `task-sg` 5432; egress `task-sg` tcp 443 → `0.0.0.0/0`
- [x] 7.6 Outputs: `service_name`, `task_family`, `exec_role_arn`, `task_role_arn`, `task_sg_id`

## 8. Módulos `api-gateway-http` e `cloudfront-spa`

- [x] 8.1 Criar `modules/api-gateway-http`: HTTP API, VPC Link (subnets públicas, `vpclink-sg` sem ingress), integração `HTTP_PROXY` privada pro `listener_arn`, rota `ANY /{proxy+}`, stage `$default` auto-deploy, CORS por variável, `integration_timeout_ms` (padrão 30000)
- [x] 8.2 Criar regras de SG do consumidor: egress `vpclink-sg` → `alb-sg` tcp 80; ingress `alb-sg` ← `vpclink-sg` tcp 80; outputs `api_url`, `api_id`
- [x] 8.3 Criar `modules/cloudfront-spa`: distribuição com OAC, `default_root_object = index.html`, redirect HTTP→HTTPS, `custom_error_response` 403/404 → `/index.html` 200, domínio padrão
- [x] 8.4 Criar bucket policy do bucket `frontend` no `cloudfront-spa` (`cloudfront.amazonaws.com` + `AWS:SourceArn` da distribuição); outputs `distribution_id`, `distribution_arn`, `domain_name`

## 9. Wiring da raiz

- [x] 9.1 Em `infra/main.tf`: `random_password` (sem caracteres problemáticos p/ JDBC/URL) e locals de nomenclatura `rag-<env>`
- [x] 9.2 Instanciar `network`, `s3-bucket` (`sources` e `frontend`), `sqs-queue`, `cloudfront-spa`, `cognito-user-pool` (callbacks derivadas do domínio CloudFront)
- [x] 9.3 Instanciar `rds-postgres` (senha do `random_password`), `secret` (JSON com `username`, `password`, `host`, `port`, `dbname`, `openrouter_api_key`), `alb`, `ecs-cluster`
- [x] 9.4 Montar `aws_iam_policy_document` da task role (S3 `sources/*` + ListBucket, SQS ingest-queue) e instanciar `ecs-service`
- [x] 9.5 Instanciar `api-gateway-http` (CORS = `https://<cloudfront_domain>`)
- [x] 9.6 Declarar `infra/outputs.tf` com o contrato do CI (`ecs_cluster_name`, `ecs_service_name`, `ecs_task_family`, `ecs_exec_role_arn`, `ecs_task_role_arn`, `frontend_bucket_name`, `cloudfront_distribution_id`, `cloudfront_domain`, `api_url`, `cognito_authority`, `cognito_client_id`, `cognito_user_pool_id`); nenhum output sensível
- [x] 9.7 `terraform validate` na raiz e `terraform graph` sem ciclo entre `rds-postgres`, `alb`, `ecs-service`, `api-gateway-http`, `cloudfront-spa`, `cognito-user-pool`

## 10. Validação de plan e segurança (sem apply)

- [ ] 10.1 `terraform plan` em dev e prod com `TF_VAR_openrouter_api_key` fictícia; confirmar diferenças só de tfvars
- [ ] 10.2 Verificar no plan: sem NAT/endpoints; RDS `publicly_accessible = false`, subnet group só `db`; ALB `internal`; `db-sg` sem egress; nenhum ingress `0.0.0.0/0` em `alb-sg`/`task-sg`/`db-sg`
- [ ] 10.3 Verificar no plan: exatamente 1 `aws_secretsmanager_secret`; 2 buckets; buckets com block public e policy `Deny` non-TLS; roles com trust e condições corretos
- [x] 10.4 Rodar `terraform fmt -check -recursive` e `terraform validate` por módulo; ajustar `infra.yml` da change de CI se o path-filter/versão do Terraform divergirem

## 11. Apply em dev e verificação

- [ ] 11.1 Bootstrap manual do bucket de state (dev) e apply por camadas conforme design (decisão 16); conferir `terraform output -json`
- [ ] 11.2 Verificar isolamento real: tentar conexão direta ao IP da task e ao RDS (devem falhar); confirmar que só o caminho API GW → VPC Link → ALB responde (404 do default listener antes de existir task)
- [ ] 11.3 Spike SSE: rota de teste atrás do API Gateway emitindo eventos por >30 s; registrar resultado em `design.md` (Risks) e decidir plano B se necessário
- [ ] 11.4 Após publicar imagem (change do backend): subir `desired_count`, checar `SELECT extname FROM pg_extension` (`vector`, `hstore`) e o fluxo upload → S3 → SQS → ingestão → pgvector

## 12. Integração com CI/CD e follow-ups

- [x] 12.1 Atualizar `openspec/changes/add-cicd-github-actions` (design/tasks): deploy lê outputs do Terraform em vez de `vars.*`; deploy da app clona a última revisão da task definition e troca só a `image`; `infra.yml` valida `infra/` real
- [x] 12.2 Registrar follow-ups como changes/tasks separadas: `application-prod.yml` (sem defaults `test`/`localhost:4566`), health endpoint sem JWT, alinhar imagem local do Postgres com a versão do RDS, remover box `dns` de `docs/solution.drawio` (`FOLLOWUPS.md`)
- [x] 12.3 Executar `openspec validate add-terraform-infra` e corrigir pendências
