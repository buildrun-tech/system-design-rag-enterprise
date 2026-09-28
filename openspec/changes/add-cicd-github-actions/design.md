## Context

Monorepo com 3 trilhas independentes: `app/backend-api` (Spring Boot 4.1.0 / Java 25, Maven wrapper), `app/frontend` (React 19 / Vite, npm), `infra/` (Terraform, hoje vazio — `main.tf`/`variables.tf`/`outputs.tf` com 0 bytes, só `terraform.tfvars` parcial em `envs/dev` e `envs/prod`). Nenhum workflow GitHub Actions existe. Nenhum Dockerfile existe.

**Esta change entrega os workflows `.github/workflows/*.yml` (+ Dockerfile do backend) e o ciclo terraform plan/apply/destroy por ambiente, com state em S3 (`infra/backend.tf`).**

> **Atualização pós `add-terraform-infra`:** a infra real (VPC, ECS, RDS, S3, CloudFront, Cognito, API Gateway) já existe em `infra/modules/*` com um `aws_ecs_service` de task definition bootstrap (`lifecycle.ignore_changes = [task_definition, desired_count]`) e outputs estáveis (ver contrato em `add-terraform-infra/specs/terraform-foundation/spec.md`). Isso substitui a premissa original desta seção (repository variables vazias) e a Non-Goal "Terraform não vai gerenciar task definition/service": os jobs de deploy agora leem `terraform output -json` e clonam a revisão vigente da task definition em vez de registrar um template próprio. Ver decisões 2.1 e Non-Goals abaixo.

Referência de estrutura: projeto anterior `system-design-interview-ticketmasters` (`.github/workflows/deploy.yml` + `terraform/destroy_config.json`), que resolve OIDC por ambiente, promoção de imagem dev→prod sem rebuild e gate de destroy via JSON. Esse projeto era monolito de 1 app só — aqui precisa path-filter pras 3 trilhas.

Pré-requisitos externos já criados manualmente (fora do escopo desta change):
- Role OIDC única, dev e prod: `arn:aws:iam::069765036136:role/ghactions-rag-enterprise`
- ECR: `069765036136.dkr.ecr.us-east-2.amazonaws.com/buildrun-ragenterprise/develop` e `.../production`
- Região: `us-east-2`

## Goals / Non-Goals

**Goals:**
- CI (pre-merge) roda só a trilha que mudou, bloqueia merge se build/teste falhar.
- CD (pos-merge) builda/publica artefatos (imagem Docker, `dist/` frontend) e faz deploy neles, com deploy da app separado da criação de infra (transparência: dois jobs distintos, dois propósitos).
- Prod nunca builda de novo — promove o artefato já validado em dev (imagem Docker + `dist/`), reduzindo risco de drift e simplificando rollback (basta re-apontar pra tag/artefato anterior).
- Terraform destroy nunca roda sem gate explícito (`infra/destroy_config.json`).

**Non-Goals:**
- Não gerenciar ECR via Terraform (pré-existente, manual).
- Não criar a role OIDC via Terraform (pré-existente, manual).
- Não mexer em código de domínio da aplicação.
- Não implementar múltiplos ambientes além de dev/prod (sem staging, sem preview por PR).
- Terraform gerencia só a task definition **bootstrap** (revisão 1, `desired_count = 0`); revisões seguintes (troca de `image`) são responsabilidade do CI via clone da revisão vigente — Terraform nunca registra uma revisão nova depois do apply inicial (`lifecycle.ignore_changes`).

## Decisions

**1. 3 workflows separados por trilha (`backend.yml`, `frontend.yml`, `infra.yml`), cada um com jobs PR e push, em vez de 1 workflow monolítico.**
Alternativa considerada: workflow único orquestrando tudo (como o ticketmaster). Rejeitada porque aqui há 3 trilhas independentes com path-filter — um único arquivo reagindo a qualquer mudança em qualquer trilha vira um `if` gigante difícil de ler. Arquivo por trilha deixa cada pipeline pequeno e o path-filter (`dorny/paths-filter` ou `paths:` do trigger) natural por arquivo.

**2. Deploy da app (register-task-definition + update-service) é job separado do `terraform apply`.**
Pedido explícito do usuário: separar "criar/atualizar infraestrutura" de "fazer deploy da aplicação" no pipeline, para transparência. Terraform só registra a revisão **bootstrap** (1x, no apply inicial); toda revisão seguinte é do CI. Trade-off: perde o padrão do ticketmaster (terraform como fonte única de verdade do ECS), ganha clareza — cada job tem um único efeito colateral (infra OU app), mais fácil de auditar/reexecutar isoladamente (ex: re-rodar só o deploy sem tocar infra). O `infra.yml` tem `plan` (PR) e `apply` (push) próprios, separados dos jobs de deploy da app.
O job de deploy da app faz `aws ecs describe-task-definition --task-definition <ecs_task_family>` (output do Terraform), troca só o campo `image` do container `app` e chama `register-task-definition` + `update-service --task-definition <nova revisão> --desired-count`. `environment`/`secrets` vêm sempre da revisão vigente (definidos pelo Terraform), nunca de um template no repositório do CI.

**2.2. Terraform por ambiente com state em S3.** `develop` usa `infra/envs/dev/terraform.tfvars`, `main` usa `infra/envs/prod/terraform.tfvars`. Backend S3 parcial (`infra/backend.tf`, `use_lockfile = true`); bucket/region vêm do env do workflow (`TF_STATE_BUCKET`, `TF_STATE_REGION`) e a key é `rag-enterprise/<env>/terraform.tfstate`, via `-backend-config` no init (composite action `terraform-init`). PR roda `plan -lock=false`; push roda `plan -out` + `apply` do plano salvo, com `environment` dev/prod (reviewers opcionais em prod).

**2.1. Nomes de cluster ECS, bucket S3, distribuição CloudFront (e demais valores do contrato) vêm de `terraform output -json`, não de repository variables.**
Superado pela `add-terraform-infra`: o state agora existe e exporta o contrato completo (`ecs_cluster_name`, `ecs_service_name`, `ecs_task_family`, `ecs_exec_role_arn`, `ecs_task_role_arn`, `frontend_bucket_name`, `cloudfront_distribution_id`, `cloudfront_domain`, `api_url`, `cognito_authority`, `cognito_client_id`, `cognito_user_pool_id`). O job de deploy roda `terraform output -json -var-file=envs/<env>/terraform.tfvars` (ou lê do plano aplicado no job de infra anterior, via artifact) e usa os valores diretamente — nenhum `vars.*` de repositório para esses nomes. `vars.*` seguem existindo só para o que o Terraform não expõe (ex: `TF_STATE_BUCKET`/`TF_STATE_REGION` do backend).

**3. Prod reusa exatamente os mesmos artefatos do dev (imagem Docker via retag, `dist/` via reuso do artifact do GitHub Actions), nunca rebuilda.**
Motivo do usuário: evitar duplicidade de artefato e permitir rollback trivial (reaponta pra tag/artifact anterior, garantidamente já testado em dev). Implica: o job de push pra `main` depende de encontrar a imagem/`dist/` do commit correspondente que passou por `develop` — usar `github.sha` como chave de correlação (tag de imagem `<sha>-dev` promovida pra `<sha>-prod`; artifact do `dist/` recuperado via `actions/download-artifact` com `run-id` do workflow de dev, ou re-upload com nome fixo por sha).

**4. Autenticação via OIDC, role única para dev e prod.**
Sem `AWS_ROLE_ARN_DEV`/`AWS_ROLE_ARN_PROD` distintos (diferente do ticketmaster) — só uma role (`ghactions-rag-enterprise`) já provisionada manualmente cobre os dois ambientes. Simplifica os workflows (não precisa `env.ENVIRONMENT == 'prod' && secrets.X || secrets.Y`).

**5. `terraform destroy` gateado por `infra/destroy_config.json` (`{"dev": bool, "prod": bool}`), lido via `jq` num job manual/condicional — nunca automático no push normal.**
Mesmo padrão do ticketmaster. Evita destruição acidental de infra em todo merge.

**6. Dockerfile multi-stage novo para o backend usando Spring Boot layertools (`java -Djarmode=layertools -jar app.jar extract` em stage builder, copy dos layers pra imagem final).**
Alternativa: `spring-boot:build-image` (Cloud Native Buildpacks) — descartada porque gera imagem sem Dockerfile explícito, menos controle/transparência sobre a imagem final e mais difícil de debugar localmente. Layertools é padrão Spring Boot, cacheia melhor (layers de dependência mudam menos que o código da app).

## Risks / Trade-offs

- **[Risco] Correlacionar artifact `dist/` do dev com o push em `main`, já que não há rebuild** → Mitigação: usar `github.sha` do commit de merge como chave; se `main` for fast-forward de `develop`, o sha é o mesmo commit, `actions/download-artifact` busca pelo run que gerou aquele sha (ou artifact fica retido com nome `frontend-dist-<sha>` e retention maior, ~7 dias, tempo suficiente pra promover).
- **[Risco] Sem rebuild em prod, se `main` não for fast-forward exato de `develop` (merge commit diferente) o sha não bate e a promoção falha** → Mitigação: documentar que merge pra `main` deve ser fast-forward/sem squash que altere o sha do conteúdo testado; job de promoção falha explicitamente (não silenciosamente) se não achar o artefato correspondente.
- **[Risco] Deploy jobs (ECS, S3/CloudFront) dependem da infra da `add-terraform-infra` já ter sido aplicada no ambiente** → Mitigação: `deploy-*` jobs falham explicitamente (erro do `terraform output`/`describe-task-definition`) se o apply de infra ainda não rodou; documentado como pré-requisito de ordem, não um bug. `deploy-*` jobs não bloqueiam o merge (são pos-merge).
- **[Trade-off] Task definition fica em dois donos: Terraform (bootstrap) e CI (revisões de imagem)** → mitigado pelo `lifecycle.ignore_changes` no `aws_ecs_service` e pelo CI sempre clonar a revisão vigente antes de trocar a imagem, preservando `environment`/`secrets` definidos pelo Terraform. Mudança de env feita no Terraform só chega ao service no próximo deploy do CI (não é imediata).

## Migration Plan

1. Criar Dockerfile do backend, validar build local.
2. Criar workflows de CI (pre-merge) primeiro — sem risco, só valida build/teste.
3. Criar workflows de CD (pos-merge) pra dev, testar num push controlado (deploy jobs vão falhar até infra/vars existirem — validar só a parte de build/push de artefato).
4. Criar workflow de promoção pra prod, testar com um merge real pra `main`.
5. Sem rollback automatizado nesta change — rollback é re-rodar o deploy apontando pra tag/artifact anterior manualmente (documentar no README).
6. Infra real já entregue pela `add-terraform-infra`; os jobs de deploy passam a funcionar de ponta a ponta assim que essa change for aplicada em cada ambiente (dev primeiro, prod depois) e o backend tiver os pré-requisitos de ECS (profile `prod`, health endpoint sem JWT — ver `add-terraform-infra/design.md`, seção Risks).

## Open Questions

- Nenhuma pendente — decisões fechadas com o usuário durante a exploração (branch/ambiente, OIDC, ECR, separação infra/deploy, reuso de artefato em prod).
