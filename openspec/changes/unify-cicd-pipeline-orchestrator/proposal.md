## Why

Hoje `backend.yml`, `frontend.yml` e `infra.yml` são workflows independentes disparados pelo mesmo `push`. Os jobs de deploy (`deploy-backend-*`, `deploy-frontend-*`) leem `terraform output` do state enquanto o `terraform apply` do mesmo commit ainda está rodando: leem valores antigos (cluster, bucket, distribuição de antes do apply) ou nenhum valor (primeiro apply), e podem atualizar um ECS service que o apply está recriando. Nada no grafo de execução garante a ordem "infra primeiro, app depois".

## What Changes

- Novo `.github/workflows/pipeline.yml`: único entrypoint (`pull_request` e `push` em `develop|main`). Substitui os gatilhos e os `paths:` dos três workflows atuais.
- Job `changes` (`dorny/paths-filter`) calcula quais trilhas mudaram (`infra`, `backend`, `frontend`) e alimenta o `if:` de cada trilha.
- `infra.yml`, `backend.yml` e `frontend.yml` viram workflows reutilizáveis (`on: workflow_call`), chamados pelo `pipeline.yml`. `infra.yml` mantém também `workflow_dispatch` (destroy manual, sem mudança de comportamento).
- Ordem declarada no grafo: as trilhas de **push** de backend e frontend têm `needs: [changes, infra]` e só rodam se `infra` terminou `success` ou `skipped` (commit sem mudança em `infra/**`). Se `infra` falha ou é cancelado, backend e frontend não rodam.
- Trilhas de **PR** (CI de backend/frontend, `ci` + `plan` de infra) não dependem umas das outras, então PR continua rápido e um `plan` vermelho não bloqueia o CI de app.
- `promote-frontend-prod` deixa de buscar o run de `frontend.yml` em `develop` e passa a localizar o artifact `frontend-dist-<sha>` pela API de artifacts (independe do status do run inteiro).
- **BREAKING (operacional)**: os nomes dos status checks mudam (`<job do pipeline> / <job do workflow chamado>`). Branch protection de `develop`/`main` precisa ser atualizada para os novos nomes.

## Capabilities

### New Capabilities
- `ci-cd-orchestration`: pipeline único que orquestra as trilhas infra/backend/frontend, garante que deploy de aplicação só ocorre depois do `terraform apply` do mesmo commit (ou sem ele, quando `infra/` não mudou) e roda apenas as trilhas afetadas.

### Modified Capabilities
(nenhuma — `ci-cd-pipeline` ainda vive em `openspec/changes/add-cicd-github-actions` e não foi arquivada em `openspec/specs/`; os requisitos novos são aditivos e ficam em capability própria)

## Impact

- Arquivos: novo `.github/workflows/pipeline.yml`; editados `.github/workflows/{infra,backend,frontend}.yml` (só gatilho + ajuste do promote do frontend). Composite actions em `.github/actions/**` não mudam.
- GitHub: branch protection (checks obrigatórios renomeados); Environments `dev`/`prod` e required reviewers seguem iguais.
- AWS: sem mudança de recurso. Role OIDC `ghactions-rag-enterprise` (criada fora do repo) precisa continuar aceitando os tokens: jobs com `environment:` mantêm `sub` `...:environment:<env>`; jobs sem `environment:` mantêm `...:ref:refs/heads/<branch>`. Só quebraria se a trust policy filtrasse por `job_workflow_ref`.
- Comportamento: em push, build de imagem/dist de backend e frontend passa a começar depois do `infra` (menos paralelismo, ver design.md).
- Sem impacto em código de domínio (backend/frontend) nem no Terraform.
