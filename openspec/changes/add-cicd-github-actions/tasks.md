## 1. Backend — Dockerfile

- [x] 1.1 Criar `app/backend-api/Dockerfile` multi-stage (build com `./mvnw package`, runtime com layertools extraído)
- [x] 1.2 Validar build local (`docker build` + `docker run`, checar app sobe e responde)
- [x] 1.3 Rodar java-quality-gate sobre o backend antes de seguir (skill `java-quality-gate`) — só coverage rodou (96.61%, PASS); mutação pulada a pedido do usuário

## 2. Workflows — CI pre-merge

- [x] 2.1 Criar `.github/workflows/backend.yml`, job PR: path-filter `app/backend-api/**`, roda `mvn build` + `mvn test`
- [x] 2.2 Criar `.github/workflows/frontend.yml`, job PR: path-filter `app/frontend/**`, roda `npm run build` + `npm run lint`
- [x] 2.3 Criar `.github/workflows/infra.yml`, job PR: path-filter `infra/**`, roda `terraform fmt -check` + `terraform validate` (infra ainda vazia — job passa trivialmente até change futura escrever os recursos)
- [ ] 2.4 Abrir PR de teste em cada trilha, confirmar que só o job certo dispara

## 3. Workflows — CD dev (push em develop)

- [x] 3.1 `backend.yml` job push-dev: configurar OIDC (`aws-actions/configure-aws-credentials`, role `ghactions-rag-enterprise`), `docker build` + push pra ECR `.../develop:<sha>-dev`
- [x] 3.2 `frontend.yml` job push-dev: `npm run build`, upload artifact `dist/` nomeado com sha, retention adequada (~7 dias)
- [x] 3.3 Novo job `deploy-backend-dev`: `aws ecs register-task-definition` com a imagem `<sha>-dev` no cluster de `vars.ECS_CLUSTER_NAME`, `aws ecs update-service --force-new-deployment`, verificar rollout via `describe-services`
- [x] 3.4 Novo job `deploy-frontend-dev`: `aws s3 sync` do `dist/` pro bucket de `vars.FRONTEND_BUCKET_NAME`, `aws cloudfront create-invalidation` na distribuição de `vars.CLOUDFRONT_DISTRIBUTION_ID`
- [x] 3.5 Documentar no README/`ARCHITECTURE.md` que `deploy-backend-dev`/`deploy-frontend-dev` só funcionam depois que a infra existir e as repository variables (`ECS_CLUSTER_NAME`, `FRONTEND_BUCKET_NAME`, `CLOUDFRONT_DISTRIBUTION_ID`) forem preenchidas

## 4. Workflows — CD prod (push em main)

- [x] 4.1 Job `promote-backend-prod`: localizar imagem `.../develop:<sha>-dev` do sha do commit, `docker pull` + retag + push `.../production:<sha>-prod` (sem rebuild), falhar explicitamente se não achar
- [x] 4.2 Job `promote-frontend-prod`: baixar artifact `dist/` retido do workflow de dev pelo mesmo sha (sem rebuild), falhar explicitamente se não achar
- [x] 4.3 Job `deploy-backend-prod`: register-task-definition com `<sha>-prod` no cluster de `vars.ECS_CLUSTER_NAME` (prod), update-service, verificar rollout
- [x] 4.4 Job `deploy-frontend-prod`: s3 sync do artifact promovido pro bucket de `vars.FRONTEND_BUCKET_NAME` (prod), invalidar CloudFront
- [ ] 4.5 Testar merge real `develop` → `main`, confirmar promoção sem rebuild (deploy real só validável depois que infra/vars existirem)

## 5. Terraform destroy (gate)

- [x] 5.1 Job de destroy (workflow_dispatch manual), lê `infra/destroy_config.json` via `jq`
- [x] 5.2 Aborta sem executar destroy se flag do ambiente for `false`
- [x] 5.3 Executa `terraform destroy` só se flag `true` e ambiente selecionado manualmente (sem efeito real até haver infra provisionada)

## 6. Documentação

- [x] 6.1 Atualizar `ARCHITECTURE.md` com o fluxo de CI/CD e diagrama das trilhas
- [x] 6.2 Documentar processo de rollback manual (reapontar deploy pra tag/artifact anterior) no README ou `ARCHITECTURE.md`
- [x] 6.3 Listar repository variables/secrets necessários (`ECS_CLUSTER_NAME`, `FRONTEND_BUCKET_NAME`, `CLOUDFRONT_DISTRIBUTION_ID`, role OIDC) como pré-requisito pra pipeline funcionar de ponta a ponta

## 7. Terraform plan/apply por ambiente (state S3)

- [x] 7.1 `infra/backend.tf` com backend S3 parcial (`use_lockfile = true`); bucket/region/key via `-backend-config`
- [x] 7.2 Composite action `.github/actions/terraform-init` (init com backend S3, key por ambiente)
- [x] 7.3 `infra.yml`: job `plan` em PR (ambiente pela branch base), job `apply` em push develop|main (`envs/dev` | `envs/prod`), destroy reusa a action de init
- [x] 7.4 Bucket S3 dummy em `infra/` (`main.tf`, `variables.tf`, `outputs.tf`, tfvars) só pra exercitar a pipeline
- [ ] 7.5 Confirmar permissões S3 (state) na role `ghactions-rag-enterprise` e criar Environments `dev`/`prod` no GitHub; validar plan/apply reais
