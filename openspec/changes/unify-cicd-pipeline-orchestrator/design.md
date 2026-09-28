## Context

Três workflows independentes (`infra`, `backend`, `frontend`) reagem ao mesmo `push`. O deploy de app depende de outputs do Terraform (`ecs_cluster_name`, `frontend_bucket_name`, `cloudfront_distribution_id`, ...) via composite action `terraform-outputs`, mas o `apply` roda em outro workflow, sem relação de ordem.

```
HOJE
push develop ─┬─ infra.yml    apply ················ (minutos)
              ├─ backend.yml  push-dev → deploy ──┐
              └─ frontend.yml push-dev → deploy ──┤ lê state durante o apply
                                                  ▼ (valor antigo / vazio)

DEPOIS
push develop ─ pipeline.yml
                 changes ─▶ infra (apply) ─▶ backend-cd  (push-dev → deploy)
                                         └─▶ frontend-cd (push-dev → deploy)
               PR: changes ─▶ infra (ci + plan) | backend-ci | frontend-ci  (independentes)
```

Fatos do repo que condicionam o desenho:
- Role OIDC `ghactions-rag-enterprise` e repositórios ECR (`buildrun-ragenterprise/develop|production`) são criados fora do Terraform (`infra/` não tem `aws_ecr_repository` nem trust policy). Logo `promote-backend-prod` (retag de imagem) **não** depende do `apply`; só o deploy depende.
- Nos workflows reutilizáveis, o contexto `github.*` é o do chamador (`event_name`, `ref`, `ref_name`, `base_ref`, `sha`). Os `if: github.event_name == ...`, `TARGET_ENV` e `concurrency` atuais continuam válidos sem alteração.
- `needs` só existe entre jobs do mesmo workflow: um job do chamador não pode ser dependência de um job *interno* do workflow chamado.

## Goals / Non-Goals

**Goals:**
- Deploy de app nunca roda antes nem durante o `terraform apply` do mesmo commit.
- Commit que não toca `infra/**` faz deploy de app normalmente (infra `skipped` não bloqueia).
- Falha/cancelamento do `apply` impede deploy de app.
- Um só lugar define gatilhos, filtros de path e a ordem entre trilhas.
- Manter o comportamento existente de promoção sem rebuild, destroy gateado e aprovação por Environment.

**Non-Goals:**
- Não mudar o Terraform, as composite actions nem a role OIDC.
- Não paralelizar build com apply (ver Decisão 4).
- Não introduzir deploy de app dentro do apply nem migrar para outra ferramenta (ex.: CodePipeline).
- Não arquivar/alterar a spec `ci-cd-pipeline` de `add-cicd-github-actions`.

## Decisions

1. **Orquestrador único `pipeline.yml` + `workflow_call`.** Os três arquivos viram reutilizáveis; o `pipeline.yml` contém gatilhos, filtro e `needs`. Alternativas: (a) `workflow_run` — descartado: o path filter do `infra` impede o run em commit só de app, então o deploy nunca dispararia, e o `github.sha` do contexto muda; (b) step de polling (`gh run watch`) nos deploys — descartado: correlação por sha, corrida de criação de run e duas fontes de verdade para a ordem; (c) `concurrency` compartilhado — descartado: serializa mas não ordena, e o grupo guarda só 1 pendente.

2. **Path filter no job `changes` (`dorny/paths-filter@v3`), não em `on.paths`.** Um único filtro cobre PR e push. Filtros:
   - `infra`: `infra/**`, `.github/workflows/infra.yml`, `.github/workflows/pipeline.yml`, `.github/actions/terraform-init/**`
   - `backend`: `app/backend-api/**`, `.github/workflows/backend.yml`, `.github/workflows/pipeline.yml`, `.github/actions/deploy-ecs/**`, `.github/actions/terraform-outputs/**`
   - `frontend`: `app/frontend/**`, `.github/workflows/frontend.yml`, `.github/workflows/pipeline.yml`, `.github/actions/terraform-outputs/**`
   Em PR o filtro usa a API (precisa `pull-requests: read`); em push compara `before..after`. Alterar só `pipeline.yml` aciona as três trilhas (em push: `apply` sem diff e redeploy idempotente) — aceito para que a mudança do próprio orquestrador seja exercitada.

3. **Jobs de PR e de push separados no chamador.**
   - PR: `infra` (chama `infra.yml`: `ci` + `plan`), `backend-ci`, `frontend-ci` — sem `needs` entre si.
   - Push: `infra`, depois `backend-cd` / `frontend-cd` com
     `needs: [changes, infra]` e
     `if: ${{ !cancelled() && github.event_name == 'push' && needs.changes.outputs.<trilha> == 'true' && (needs.infra.result == 'success' || needs.infra.result == 'skipped') }}`.
   O `!cancelled()` é obrigatório: sem ele, `infra` `skipped` propaga skip para o deploy. Os `if:` internos dos workflows reutilizáveis (`event_name == 'pull_request'|'push'`) ficam como defesa em profundidade.

4. **Build espera o `apply` (perda de paralelismo aceita).** Por causa da restrição de `needs`, `backend-cd`/`frontend-cd` chamam o workflow inteiro (build + push + deploy) depois do `infra`. Ganho de manter paralelo seria esconder o tempo do build (~poucos minutos) atrás do `apply` (RDS/CloudFront podem passar de 10 min), mas exigiria dividir cada workflow em `*-build.yml` e `*-deploy.yml`. Upgrade path documentado: dividir só se o tempo de pipeline incomodar. Alternativa descartada por ora: deixar o build fora da dependência (mais 2 arquivos + 2 chamadores).

5. **`infra.yml` mantém `workflow_dispatch`.** Destroy manual continua rodando standalone (gate `destroy_config.json` + Environment com reviewers). Não passa pelo `pipeline.yml`, então não há `needs` nem path filter envolvidos.

6. **Permissões no topo do `pipeline.yml`**: `contents: read`, `id-token: write`, `actions: read`, `pull-requests: read`. Workflow chamado só pode reduzir permissões, nunca ampliar; os jobs internos já declaram as suas.

7. **Promote do frontend por API de artifacts.** O `gh run list --workflow frontend.yml ... --status success` deixa de servir: o run agora é do `pipeline.yml` e falha se *qualquer* trilha falhar. Novo passo: `gh api repos/$REPO/actions/artifacts?name=frontend-dist-$GITHUB_SHA` → primeiro artifact não expirado → `workflow_run.id` → `gh run download <id> -n frontend-dist-$SHA`. Se nada for encontrado, falha explícita (mesma mensagem de hoje, sem rebuild).

8. **Concurrency.** Mantém `terraform-<ref_name>` no job `apply` (dentro de `infra.yml`), `cancel-in-progress: false`. Não se adiciona grupo no `pipeline.yml`: dois pushes seguidos não se cancelam; o state lock cobre o Terraform e os deploys seguem a ordem de chegada.

## Risks / Trade-offs

- [Status checks renomeados quebram branch protection] → atualizar os checks obrigatórios em `develop`/`main` após o primeiro run do `pipeline.yml` (tarefa explícita); nomes finais aparecem na aba Checks de uma PR de teste.
- [Trust policy da role OIDC filtra por `job_workflow_ref`] → improvável (não há evidência no repo; `sub` por `environment`/`ref` não muda). Validar com um push de teste em `develop`; se falhar, ajustar a trust policy fora do repo.
- [`infra` skipped propagando skip para deploy] → `!cancelled()` + checagem explícita de `result`; coberto por cenário de teste (commit só de app).
- [Push que altera `pipeline.yml` redeploya tudo] → aceito (idempotente); reduzir filtrando `pipeline.yml` só em `infra` se incomodar.
- [Build serializado atrás do apply] → trade-off da Decisão 4.
- [Prod: `apply` aguarda aprovação manual, deploy de app espera junto] → comportamento desejado (app não sobe antes da infra aprovada); o run fica "waiting" no Environment `prod`.
- [Artifact `frontend-dist-<sha>` expira em 7 dias] → inalterado; mensagem de erro do promote continua explícita.
- [Primeiro push com state vazio] → `infra` roda apply antes; se falhar, app não deploya (era o bug original).

## Migration Plan

1. Abrir PR de `unify-cicd-pipeline-orchestrator` para `develop`; validar em PR: só `pipeline.yml` roda, checks de CI/plan aparecem com novos nomes.
2. Atualizar branch protection com os novos nomes de check (manter os antigos até o merge para não bloquear a própria PR, depois removê-los).
3. Merge em `develop`: validar cenários (a) commit só `app/` → infra `skipped`, deploy roda; (b) commit `infra/` + `app/` → deploy só depois do apply; (c) apply falhando → deploy não roda.
4. Promover para `main`: validar retag de imagem e promote do frontend por API de artifacts.
- **Rollback**: `git revert` do commit restaura os três workflows independentes (gatilhos voltam a `push`/`pull_request`); nenhum recurso AWS ou state é tocado por esta change.

## Open Questions

- A trust policy da role `ghactions-rag-enterprise` usa `job_workflow_ref`? (validado empiricamente no passo 3 da migração.)
- Vale dividir build/deploy (Decisão 4) já, ou só se o tempo total incomodar?
