## Context

Monorepo com 3 trilhas independentes: `app/backend-api` (Spring Boot 4.1.0 / Java 25, Maven wrapper), `app/frontend` (React 19 / Vite, npm), `infra/` (Terraform, hoje vazio — `main.tf`/`variables.tf`/`outputs.tf` com 0 bytes, só `terraform.tfvars` parcial em `envs/dev` e `envs/prod`). Nenhum workflow GitHub Actions existe. Nenhum Dockerfile existe.

**Esta change entrega os workflows `.github/workflows/*.yml` (+ Dockerfile do backend) e o ciclo terraform plan/apply/destroy por ambiente, com state em S3 (`infra/backend.tf`).** Escrever a infra Terraform real (VPC, ECS, RDS, S3, CloudFront) é escopo de uma change futura separada; `infra/` tem só um bucket S3 dummy pra exercitar a pipeline. Consequência direta: os jobs de deploy (ECS task definition/rollout, S3 sync/CloudFront invalidation) não têm, hoje, recursos AWS reais pra apontar — leem nome de cluster/bucket/distribuição de repository variables (`vars.*`) que ficam vazias/placeholder até a infra existir. A pipeline fica completa e correta estruturalmente, mas o deploy real só funciona depois que a infra for provisionada e as vars preenchidas.

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
- **Não escrever infra Terraform real nesta change** (VPC, ALB, ECS cluster, RDS, S3, CloudFront ficam pra change futura); só o bucket dummy.
- Não gerenciar ECR via Terraform (pré-existente, manual).
- Não criar a role OIDC via Terraform (pré-existente, manual).
- Não mexer em código de domínio da aplicação.
- Não implementar múltiplos ambientes além de dev/prod (sem staging, sem preview por PR).
- Terraform (quando existir) não vai gerenciar ECS task definition nem ECS service — só cluster/rede/ALB/RDS/S3/CloudFront/IAM.

## Decisions

**1. 3 workflows separados por trilha (`backend.yml`, `frontend.yml`, `infra.yml`), cada um com jobs PR e push, em vez de 1 workflow monolítico.**
Alternativa considerada: workflow único orquestrando tudo (como o ticketmaster). Rejeitada porque aqui há 3 trilhas independentes com path-filter — um único arquivo reagindo a qualquer mudança em qualquer trilha vira um `if` gigante difícil de ler. Arquivo por trilha deixa cada pipeline pequeno e o path-filter (`dorny/paths-filter` ou `paths:` do trigger) natural por arquivo.

**2. Deploy da app (register-task-definition + update-service) é job separado do `terraform apply`.**
Pedido explícito do usuário: separar "criar/atualizar infraestrutura" de "fazer deploy da aplicação" no pipeline, para transparência. Terraform nunca escreve revisão de task definition. Trade-off: perde o padrão do ticketmaster (terraform como fonte única de verdade do ECS), ganha clareza — cada job tem um único efeito colateral (infra OU app), mais fácil de auditar/reexecutar isoladamente (ex: re-rodar só o deploy sem tocar infra). O `infra.yml` tem `plan` (PR) e `apply` (push) próprios, separados dos jobs de deploy da app.

**2.2. Terraform por ambiente com state em S3.** `develop` usa `infra/envs/dev/terraform.tfvars`, `main` usa `infra/envs/prod/terraform.tfvars`. Backend S3 parcial (`infra/backend.tf`, `use_lockfile = true`); bucket/region vêm do env do workflow (`TF_STATE_BUCKET`, `TF_STATE_REGION`) e a key é `rag-enterprise/<env>/terraform.tfstate`, via `-backend-config` no init (composite action `terraform-init`). PR roda `plan -lock=false`; push roda `plan -out` + `apply` do plano salvo, com `environment` dev/prod (reviewers opcionais em prod).

**2.1. Nomes de cluster ECS, bucket S3 e distribuição CloudFront vêm de repository variables (`vars.*`), não de `terraform output`.**
Já que infra não é provisionada nesta change, não existe state Terraform pra ler output. Os workflows referenciam `vars.ECS_CLUSTER_NAME`, `vars.FRONTEND_BUCKET_NAME`, `vars.CLOUDFRONT_DISTRIBUTION_ID` — configuradas manualmente no repo GitHub assim que a infra existir. Quando a change de infra Terraform for implementada, dá pra trocar por `terraform output` sem alterar a estrutura dos jobs de deploy (só a fonte do valor).

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
- **[Risco] Deploy jobs (ECS, S3/CloudFront) não têm infra real pra apontar nesta change** → Mitigação: valores vêm de repository variables vazias/placeholder; jobs de deploy vão falhar até alguém preencher as vars e a infra existir — comportamento esperado e documentado, não um bug. `deploy-*` jobs não bloqueiam o merge (são pos-merge), então não travam desenvolvimento no meio tempo.
- **[Trade-off] Terraform (quando existir) não vai gerenciar task definition/ECS service** → estado do ECS ficará em dois lugares (Terraform pro cluster/service base, AWS CLI pra revisões de task definition). Aceito porque foi pedido explícito do usuário; mitigado documentando claramente no workflow qual job faz o quê.

## Migration Plan

1. Criar Dockerfile do backend, validar build local.
2. Criar workflows de CI (pre-merge) primeiro — sem risco, só valida build/teste.
3. Criar workflows de CD (pos-merge) pra dev, testar num push controlado (deploy jobs vão falhar até infra/vars existirem — validar só a parte de build/push de artefato).
4. Criar workflow de promoção pra prod, testar com um merge real pra `main`.
5. Sem rollback automatizado nesta change — rollback é re-rodar o deploy apontando pra tag/artifact anterior manualmente (documentar no README).
6. Change futura: escrever infra Terraform real e preencher as repository variables — só então os jobs de deploy passam a funcionar de ponta a ponta.

## Open Questions

- Nenhuma pendente — decisões fechadas com o usuário durante a exploração (branch/ambiente, OIDC, ECR, separação infra/deploy, reuso de artefato em prod).
