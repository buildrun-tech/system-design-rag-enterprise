## Context

Hoje `aws_ecs_task_definition.this` ignora `container_definitions` e `aws_ecs_service.this` ignora `task_definition`. A action `deploy-ecs` clona a revisão que o service roda e troca só a imagem. Efeito: a configuração da task (env, secrets) fica congelada na revisão do primeiro bootstrap, e mudanças no Terraform (ex.: `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`) nunca chegam ao ECS. Revisões de task definition são imutáveis e incluem a imagem, então "promover" uma configuração nova com imagem nova sempre registra uma revisão nova.

## Goals / Non-Goals

**Goals:**
- Terraform é a única fonte da verdade da configuração da task.
- CD é dono apenas da imagem e do rollout.
- `terraform apply` nunca causa rollout.
- Promover só configuração (sem build) é possível.

**Non-Goals:**
- Blue/green ou CodeDeploy.
- Suporte a múltiplos containers/sidecars (continua 1 container, índice 0).
- Limpeza automática de revisões antigas.
- Mudanças em código da aplicação.

## Decisions

**1. Terraform dono da task definition, sem `ignore_changes` em `container_definitions`.** Mudança de env/secret gera revisão nova da família. *Alternativa:* manter o ignore e injetar env no CD — duplica a fonte da verdade e foi a causa do bug.

**2. `skip_destroy = true` na task definition.** Sem isso, o Terraform desregistra a revisão anterior ao criar a nova; a revisão do service ficaria `INACTIVE` e o ECS não conseguiria lançar tasks novas a partir dela (restart, scale, replacement).

**3. Service mantém `ignore_changes = [task_definition, desired_count]`.** `terraform apply` só registra revisão; quem aponta o service para ela é o CD. *Alternativa:* Terraform atualizar o service — reverteria a imagem para `var.app_image` (bootstrap) e acoplaria infra a deploy.

**4. A imagem em Terraform é só bootstrap (`var.app_image`).** O CD sempre sobrescreve. Uma revisão do Terraform com imagem de bootstrap nunca roda em produção porque o service não a referencia até o CD promover.

**5. `deploy-ecs` clona a última revisão da família (`describe-task-definition --task-definition <família>` sem número).** Ela é sempre o resultado mais recente de Terraform ou CD, e ambos partem da configuração do Terraform. Troca só `.containerDefinitions[0].image`, registra, `update-service --force-new-deployment`, espera estabilizar e confere que o service roda a revisão registrada.

**6. Redeploy como workflow separado (`redeploy-backend.yml`, `workflow_dispatch` com input `environment`).** Lê a imagem do container da revisão que o service roda hoje e chama a mesma action `deploy-ecs`. Serve para promover mudança de configuração sem build. `backend.yml` só tem `workflow_call`, então não é alterado além de passar a família.

## Risks / Trade-offs

- [Mudança de env só entra quando alguém promove] → Intencional; documentado, e o workflow de redeploy cobre o caso.
- [Revisão do Terraform (imagem de bootstrap) vira "a última" e um deploy concorrente clona dela] → Sem problema: a action sempre sobrescreve a imagem; deploys concorrentes continuam serializados pelo `concurrency` do workflow.
- [Acúmulo de revisões na família] → Aceito; limpeza fica como follow-up.
- [Primeiro apply após a mudança cria revisão nova com diff grande no plan] → Revisar o plan; não há rollout.

## Migration Plan

1. Merge do change (infra aplica via `infra.yml`): cria nova revisão da família com a config atual, sem rollout.
2. Rodar `redeploy-backend.yml` em dev: valida o fluxo; confirmar `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL` nas tasks.
3. Repetir em prod depois do merge em `main`.
4. Rollback: reverter o PR e usar `aws ecs update-service --task-definition <revisão anterior>`; as revisões antigas continuam `ACTIVE` por causa do `skip_destroy`.

## Open Questions

- Política de retenção de revisões antigas (follow-up).
