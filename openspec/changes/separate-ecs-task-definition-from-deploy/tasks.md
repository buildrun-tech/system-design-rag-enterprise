## 1. Terraform

- [x] 1.1 Em `infra/modules/ecs-service/main.tf`, remover `ignore_changes = [container_definitions]` do `aws_ecs_task_definition.this` e adicionar `skip_destroy = true`
- [x] 1.2 Atualizar o comentário "Task definition bootstrap" explicando que a imagem é bootstrap e o service ignora `task_definition`
- [x] 1.3 `terraform fmt` e `terraform validate`; revisar o `plan` (nova revisão, service inalterado)

## 2. Action deploy-ecs

- [x] 2.1 Adicionar input `family` em `.github/actions/deploy-ecs/action.yml`
- [x] 2.2 Clonar a última revisão da família (`describe-task-definition --task-definition "$FAMILY"`) em vez da revisão do service, trocando só `.containerDefinitions[0].image`
- [x] 2.3 Manter registro, `update-service`, `services-stable` e a verificação de convergência
- [x] 2.4 Atualizar a description da action

## 3. Workflows

- [x] 3.1 Em `backend.yml`, passar `family: ${{ steps.tf.outputs.ecs_task_family }}` nos jobs de deploy dev e prod
- [x] 3.2 Criar `.github/workflows/redeploy-backend.yml` (`workflow_dispatch`, input `environment` dev|prod) que lê a imagem atual do service e chama `deploy-ecs`
- [x] 3.3 Usar `concurrency` por ambiente no redeploy e nos deploys de `backend.yml` para serializá-los

## 4. Validação

- [ ] 4.1 Merge em `develop`: confirmar que o apply cria revisão nova sem rollout
- [ ] 4.2 Rodar `redeploy-backend.yml` em dev e confirmar `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` nas tasks em execução
- [ ] 4.3 Repetir em prod após o merge em `main`
