## ADDED Requirements

### Requirement: CI pre-merge por trilha
O sistema SHALL executar build e teste automatizados em pull requests para `develop` e `main`, rodando apenas a trilha (backend, frontend, infra) cujos arquivos mudaram no PR.

#### Scenario: PR muda só backend
- **WHEN** um pull request altera arquivos em `app/backend-api/**`
- **THEN** o workflow executa `mvn build` e `mvn test` do backend, e NÃO executa os jobs de frontend nem de infra

#### Scenario: PR muda só frontend
- **WHEN** um pull request altera arquivos em `app/frontend/**`
- **THEN** o workflow executa `npm run build` e `npm run lint` do frontend, e NÃO executa os jobs de backend nem de infra

#### Scenario: PR muda só infra
- **WHEN** um pull request altera arquivos em `infra/**`
- **THEN** o workflow executa `terraform fmt -check` e `terraform validate`, e NÃO executa os jobs de backend nem de frontend

#### Scenario: PR falha teste bloqueia merge
- **WHEN** `mvn test`, `npm run lint` ou `terraform validate` falha
- **THEN** o check do pull request fica vermelho, impedindo merge (branch protection)

### Requirement: Build e publicação de imagem Docker do backend em dev
O sistema SHALL, ao detectar push em `develop` com mudança em `app/backend-api/**`, buildar a imagem Docker do backend e publicá-la no ECR dev com tag `<sha>-dev`.

#### Scenario: Push em develop com mudança de backend
- **WHEN** um push em `develop` contém mudanças em `app/backend-api/**`
- **THEN** o workflow builda a imagem via `Dockerfile` do backend e faz push para `069765036136.dkr.ecr.us-east-2.amazonaws.com/buildrun-ragenterprise/develop:<sha>-dev`

### Requirement: Build de artefato frontend em dev
O sistema SHALL, ao detectar push em `develop` com mudança em `app/frontend/**`, buildar o frontend e reter o diretório `dist/` como artifact do workflow, identificado pelo sha do commit.

#### Scenario: Push em develop com mudança de frontend
- **WHEN** um push em `develop` contém mudanças em `app/frontend/**`
- **THEN** o workflow executa `npm run build` e faz upload do `dist/` como artifact nomeado com o sha do commit

### Requirement: Deploy da aplicação backend separado da infraestrutura
O sistema SHALL registrar uma nova revisão de ECS task definition e atualizar o ECS service em um job próprio, que NUNCA executa comandos `terraform` — mesmo quando um job de `terraform apply` existir no futuro, o job de deploy permanece distinto dele. Nome do cluster ECS alvo vem de repository variable (`vars.ECS_CLUSTER_NAME`), não de output do Terraform.

#### Scenario: Deploy backend após imagem publicada em dev
- **WHEN** a imagem Docker do backend foi publicada com sucesso no ECR dev
- **THEN** um job separado executa `aws ecs register-task-definition` referenciando a nova imagem e `aws ecs update-service --force-new-deployment` no cluster de `vars.ECS_CLUSTER_NAME`, sem que esse job execute qualquer comando `terraform`

#### Scenario: Verificação de rollout
- **WHEN** o `update-service` é disparado
- **THEN** o workflow aguarda e confirma via `aws ecs describe-services` que a task definition em execução no service é igual à task definition recém-registrada, falhando o job caso não converja

### Requirement: Deploy do frontend para hosting estático
O sistema SHALL sincronizar o artifact `dist/` para o bucket S3 do ambiente e invalidar o cache do CloudFront, em job separado do deploy do backend. Nome do bucket e ID da distribuição vêm de repository variables (`vars.FRONTEND_BUCKET_NAME`, `vars.CLOUDFRONT_DISTRIBUTION_ID`), não de output do Terraform.

#### Scenario: Deploy frontend após build
- **WHEN** o artifact `dist/` foi gerado no push atual
- **THEN** o workflow executa `aws s3 sync` do `dist/` para o bucket de `vars.FRONTEND_BUCKET_NAME` e `aws cloudfront create-invalidation` na distribuição de `vars.CLOUDFRONT_DISTRIBUTION_ID`

### Requirement: Promoção de artefatos para produção sem rebuild
O sistema SHALL, ao detectar push em `main`, promover a imagem Docker e o artefato `dist/` já validados em `develop` para o mesmo commit sha, sem rebuildar nenhum dos dois.

#### Scenario: Promoção de imagem Docker
- **WHEN** um push ocorre em `main` para um commit sha que já tem imagem publicada em `.../develop:<sha>-dev`
- **THEN** o workflow faz `docker pull` dessa imagem, retag para `.../production:<sha>-prod` e push, sem executar `docker build`

#### Scenario: Promoção do artefato frontend
- **WHEN** um push ocorre em `main` para um commit sha que já tem artifact `dist/` retido do workflow de `develop`
- **THEN** o workflow baixa esse artifact existente (sem rodar `npm run build` novamente) e o usa para o deploy em prod

#### Scenario: Artefato correspondente não encontrado
- **WHEN** um push em `main` não encontra imagem Docker ou artifact `dist/` correspondentes ao sha em dev
- **THEN** o job de promoção falha explicitamente, sem tentar rebuild automático como fallback silencioso

### Requirement: Autenticação AWS via OIDC
O sistema SHALL autenticar todo acesso à AWS nos workflows via OpenID Connect, assumindo a role `arn:aws:iam::069765036136:role/ghactions-rag-enterprise`, sem usar access keys estáticas em secrets.

#### Scenario: Qualquer job que chama AWS CLI ou Terraform
- **WHEN** um job precisa interagir com AWS (ECR, ECS, S3, CloudFront, Terraform backend)
- **THEN** o job declara `permissions: id-token: write` e usa `aws-actions/configure-aws-credentials` com `role-to-assume: arn:aws:iam::069765036136:role/ghactions-rag-enterprise`

### Requirement: Terraform plan e apply por ambiente com state em S3
O sistema SHALL executar `terraform plan` em pull requests que alteram `infra/**` e `terraform apply` em push para `develop` (ambiente dev) e `main` (ambiente prod), usando `infra/envs/<env>/terraform.tfvars` e state remoto em S3 com lockfile, com key por ambiente.

#### Scenario: PR altera infra
- **WHEN** um pull request altera `infra/**`
- **THEN** o workflow executa `terraform fmt -check`, `validate` e `plan` (sem alterar recursos), usando o ambiente da branch base (`develop` = dev, `main` = prod)

#### Scenario: Push em develop ou main altera infra
- **WHEN** um push em `develop` (ou `main`) altera `infra/**`
- **THEN** o workflow executa `terraform plan -out` e `terraform apply` do plano salvo com `infra/envs/dev/terraform.tfvars` (ou `envs/prod`), com state em `rag-enterprise/<env>/terraform.tfstate` no bucket S3 de state

### Requirement: Gate de destruição de infraestrutura
O sistema SHALL nunca executar `terraform destroy` automaticamente em um push normal; a operação só SHALL ocorrer quando o flag correspondente ao ambiente em `infra/destroy_config.json` estiver `true` e o job for disparado explicitamente.

#### Scenario: Push normal nunca destrói
- **WHEN** um push ocorre em `develop` ou `main`
- **THEN** o workflow de CD nunca executa `terraform destroy` como parte do fluxo padrão de deploy

#### Scenario: Destroy manual com flag ativo
- **WHEN** um operador dispara o job de destroy manualmente e `infra/destroy_config.json` tem `true` para o ambiente alvo
- **THEN** o workflow executa `terraform destroy` para aquele ambiente

#### Scenario: Destroy manual com flag desativado
- **WHEN** um operador dispara o job de destroy manualmente mas `infra/destroy_config.json` tem `false` para o ambiente alvo
- **THEN** o workflow SHALL abortar sem executar `terraform destroy`
