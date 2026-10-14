## Why

O `terraform apply` não propaga mudanças de configuração da task (ex.: `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`) para o ECS: a task definition ignora `container_definitions` e o service ignora `task_definition`. O CD clona a revisão que o service roda hoje, então o CD virou, sem querer, a fonte da verdade da configuração. Resultado: env vars novas nunca chegam ao ambiente (erro `400: No models provided` em produção).

## What Changes

- Terraform volta a ser dono da definição da task (env, secrets, CPU/memória, roles, logs, health check): remove `ignore_changes = [container_definitions]` da `aws_ecs_task_definition` e adiciona `skip_destroy = true` para não desregistrar revisões em uso.
- O `aws_ecs_service` mantém `ignore_changes = [task_definition, desired_count]`: `terraform apply` registra revisão nova da família, mas nunca faz rollout.
- A action `deploy-ecs` passa a clonar a **última revisão da família Terraform** (e não a revisão que o service roda), trocar só a imagem, registrar, `update-service` e verificar convergência.
- A action passa a receber a família (output `ecs_task_family`, que já existe e já é lido por `terraform-outputs`).
- Novo workflow `workflow_dispatch` de redeploy com a imagem atualmente em execução, para promover mudanças de configuração sem build.

## Capabilities

### New Capabilities
- `ecs-deployment`: contrato de separação entre definição de infraestrutura (Terraform, dono da task definition) e deploy (CD, dono da imagem e do rollout) no ECS.

### Modified Capabilities

## Impact

- `infra/modules/ecs-service/main.tf` (task definition lifecycle, `skip_destroy`), 
- `.github/actions/deploy-ecs/action.yml` e `.github/actions/terraform-outputs`.
- `.github/workflows/backend.yml` (passa a família à action) e novo `.github/workflows/redeploy-backend.yml`.
- Sem mudança de API ou código da aplicação.
