## 1. Workflows de trilha viram reutilizáveis

- [x] 1.1 `infra.yml`: trocar `on.pull_request`/`on.push` por `on.workflow_call` (sem inputs), manter `workflow_dispatch` do destroy; jobs, `env`, `concurrency` e `if:` permanecem
- [x] 1.2 `backend.yml`: trocar `on` por `workflow_call`; atualizar o comentário de cabeçalho (gatilhos agora vêm do `pipeline.yml`)
- [x] 1.3 `frontend.yml`: trocar `on` por `workflow_call`; atualizar o comentário de cabeçalho
- [x] 1.4 `frontend.yml` `promote-frontend-prod`: localizar o artifact via `gh api repos/$GITHUB_REPOSITORY/actions/artifacts?name=frontend-dist-$GITHUB_SHA` (não expirado) → `workflow_run.id` → `gh run download`; manter mensagem de erro explícita sem rebuild

## 2. Orquestrador

- [x] 2.1 Criar `.github/workflows/pipeline.yml`: `on` `pull_request`/`push` em `develop|main`; `permissions` (`contents: read`, `id-token: write`, `actions: read`, `pull-requests: read`)
- [x] 2.2 Job `changes` com `dorny/paths-filter@v3` e os filtros `infra`/`backend`/`frontend` do design; expor outputs por trilha
- [x] 2.3 Job `infra` (`uses: ./.github/workflows/infra.yml`, `needs: changes`, `if` `infra == 'true'`)
- [x] 2.4 Jobs de PR `backend-ci` e `frontend-ci` (`needs: changes`, `if` `pull_request` + trilha alterada), sem dependência de `infra`
- [x] 2.5 Jobs de push `backend-cd` e `frontend-cd` (`needs: [changes, infra]`, `if: !cancelled() && push && trilha alterada && infra.result in (success, skipped)`)

## 3. Documentação e validação estática

- [x] 3.1 Atualizar `ARCHITECTURE.md` / `infra/README.md` (se citarem os workflows) com o fluxo do `pipeline.yml`
- [x] 3.2 Validar sintaxe: `actionlint` (ou `python -c yaml.safe_load`) nos 4 workflows; conferir que nenhum `on:` de push/PR sobrou em arquivo reutilizável
- [x] 3.3 Revisar manualmente os `if:` do `pipeline.yml` contra os cenários da spec `ci-cd-orchestration`

## 4. Validação em GitHub (manual, fora do repo)

- [ ] 4.1 Abrir PR para `develop`; conferir jobs/checks gerados pelo `pipeline` e anotar os novos nomes
- [ ] 4.2 Atualizar branch protection de `develop`/`main` com os novos checks obrigatórios; remover os antigos
- [ ] 4.3 Push em `develop` só com `app/backend-api/**`: infra `skipped`, deploy roda
- [ ] 4.4 Push em `develop` com `infra/**` + `app/**`: deploy só inicia depois do apply
- [ ] 4.5 Apply falhando (ex.: erro proposital em `plan`): backend e frontend não rodam
- [ ] 4.6 Push em `main`: retag da imagem e promote do frontend por API de artifacts; deploy de prod aguarda aprovação do Environment
- [ ] 4.7 Confirmar que o OIDC segue autenticando nos jobs chamados (Open Question do design)
