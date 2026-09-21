## Why

Projeto não tem pipeline automatizado: build, teste e deploy são manuais hoje. Sem CI, PR pode quebrar build/teste sem ninguém notar antes do merge. Sem CD, deploy de backend (ECS) e frontend (S3/CloudFront) depende de passos manuais, sujeitos a erro e sem rastro de auditoria.

**Escopo desta change: só os arquivos de GitHub Actions** (inclui o ciclo terraform plan/apply/destroy por ambiente, com state em S3). O código Terraform da infra AWS real (VPC, ECS cluster, RDS, bucket S3, CloudFront) não é escrito aqui — fica pra uma change futura; `infra/` tem só um bucket S3 dummy pra exercitar a pipeline. Os jobs de deploy desta pipeline apontam pra recursos AWS que ainda não existem (nomes vêm de repository variables, preenchidas manualmente depois); a pipeline fica pronta mas só roda deploy de fato quando a infra existir.

## What Changes

- Cria workflows GitHub Actions cobrindo as 3 trilhas do monorepo (backend Java, frontend React, infra Terraform):
  - **Pre-merge (pull_request → develop|main)**: build+test por trilha, com path-filter (só roda job da trilha que mudou). Trilha infra roda `terraform fmt -check` + `terraform validate` (sem AWS) e `terraform plan` (lê state em S3, ambiente pela branch base).
  - **Pos-merge (push → develop|main)**: build/push de imagem Docker, deploy da app (task definition + rollout ECS) e deploy do frontend (S3 sync + invalidação CloudFront), como jobs distintos. `terraform apply` (plan + apply do plano salvo) com `infra/envs/dev` em `develop` e `infra/envs/prod` em `main`, state em S3.
- Branch `develop` = ambiente dev, branch `main` = ambiente prod. Em prod, backend reusa a mesma imagem Docker do dev (retag, sem rebuild) e frontend reusa o mesmo artefato `dist/` do dev (sem rebuild) — evita drift entre o que foi testado em dev e o que vai pra prod, facilita rollback.
- Deploy da app (register-task-definition + update-service + verificação de rollout) e deploy do frontend (s3 sync + invalidação) leem nome de cluster/service ECS, bucket S3 e distribuição CloudFront de variables dos GitHub Environments `dev`/`prod` (`vars.ECS_CLUSTER_NAME`, `vars.ECS_SERVICE_NAME`, `vars.FRONTEND_BUCKET_NAME`, `vars.CLOUDFRONT_DISTRIBUTION_ID`), não de output do Terraform — a infra real ainda não existe.
- `terraform destroy` só roda com gate via `infra/destroy_config.json` (já existe no repo), nunca dispara junto do apply automático — mantido como workflow porque já existe o arquivo de gate no repo, mesmo sem infra real pra destruir ainda.
- Autenticação AWS via OIDC (`aws-actions/configure-aws-credentials`), role única já criada manualmente (`arn:aws:iam::069765036136:role/ghactions-rag-enterprise`), sem access key estática.
- Cria `Dockerfile` do backend (multi-stage, Spring Boot 4.1.0 / Java 25) — não existe hoje.

## Capabilities

### New Capabilities
- `ci-cd-pipeline`: workflows GitHub Actions (pre-merge CI e pos-merge CD) para backend, frontend e infra, incluindo build/push de imagem, deploy ECS, deploy frontend e gate de terraform destroy.

### Modified Capabilities
(nenhuma — não há spec existente sendo alterada)

## Impact

- Novos arquivos: `.github/workflows/*.yml`, `app/backend-api/Dockerfile`.
- `infra/` ganha só `backend.tf` (backend S3 parcial) e um bucket S3 dummy (`main.tf`/`variables.tf`/`outputs.tf`, `envs/*/terraform.tfvars`) pra testar a pipeline; Terraform da infra base fica pra change futura.
- Depende de: role OIDC e repositórios ECR já criados manualmente na conta AWS 069765036136, região `us-east-2`; nome de cluster ECS/bucket S3/distribuição CloudFront configurados como repository variables assim que a infra existir (fora do escopo desta change).
- Sem impacto em código de domínio (`entity`, `service`, `controller`) do backend nem do frontend.
