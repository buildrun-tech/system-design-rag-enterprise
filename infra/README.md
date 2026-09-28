# infra/

Raiz Terraform única para `dev` e `prod`. Conta AWS `069765036136`, região
`us-east-2`. Sem NAT gateway, sem ACM/Route53 (domínios padrão).

## Bootstrap manual (fora do Terraform)

Antes do primeiro `init`, criar manualmente por ambiente:

1. **Bucket de state** (`rag-enterprise-tfstate-<env>`, ver `envs/<env>/backend.hcl`):
   - versionamento habilitado
   - SSE (AES256 ou KMS)
   - block public access total
   - o state contém a senha do RDS e a chave do OpenRouter — acesso restrito à role OIDC e a quem faz apply manual
2. **Repositórios ECR** `buildrun-ragenterprise/develop` e `buildrun-ragenterprise/production` (change `add-cicd-github-actions`)
3. **Role OIDC** `ghactions-rag-enterprise` (trust do GitHub Actions), com a policy abaixo

Nenhum desses recursos é gerenciado por este Terraform.

## Permissões exigidas pela role OIDC `ghactions-rag-enterprise`

- ECR: `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, `ecr:BatchGetImage`
- ECS: `ecs:DescribeTaskDefinition`, `ecs:RegisterTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeServices`
- IAM: `iam:PassRole` **restrito** aos ARNs de `rag-<env>-exec` e `rag-<env>-task`, com condição `iam:PassedToService = ecs-tasks.amazonaws.com` — nunca `Resource: "*"`
- S3: sync no bucket `frontend` (`s3:PutObject`, `s3:DeleteObject`, `s3:ListBucket`)
- CloudFront: `cloudfront:CreateInvalidation`
- State: `s3:GetObject`/`PutObject`/`ListBucket` no bucket de state (com `use_lockfile`, sem DynamoDB)
- Apply: permissões de criação/atualização dos recursos de cada módulo (VPC, RDS, S3, SQS, Secrets Manager, Cognito, ALB, ECS, API Gateway, CloudFront) para o `terraform apply` do próprio job de infra

A mesma role cobre `dev` e `prod`; mitigar com `sub` do OIDC limitado a branches (`develop`, `main`) e GitHub Environments com aprovação manual em prod.

## Init e plan por ambiente

```bash
cd infra

terraform init -backend-config=envs/dev/backend.hcl

export TF_VAR_openrouter_api_key="<chave>"
terraform plan  -var-file=envs/dev/terraform.tfvars
terraform apply -var-file=envs/dev/terraform.tfvars
```

Trocar `dev` por `prod` (e reexecutar `init -reconfigure` ao alternar de backend)
para o outro ambiente.

## Ordem de apply em camadas (guia; Terraform resolve pelo grafo)

1. bootstrap manual (bucket de state)
2. `network`, `s3-bucket` (x2), `sqs-queue`, `cloudfront-spa`, `cognito-user-pool`
3. `rds-postgres`, `secret`, `alb`, `ecs-cluster`
4. `ecs-service` (cria as regras de SG cruzadas com `alb-sg`/`db-sg`)
5. `api-gateway-http` (cria as regras `vpclink-sg` ↔ `alb-sg`)

Use `-target=module.<nome>` só para depuração pontual.

## Destroy

`terraform destroy` só deve rodar se `destroy_config.json` tiver `true` para o
ambiente alvo, e nunca dentro do mesmo fluxo automático do `apply`. Em prod,
`deletion_protection = true` no RDS bloqueia o destroy até ser desligado
manualmente.
