## ADDED Requirements

### Requirement: Layout da raiz Terraform e módulos
O repositório SHALL manter uma única raiz Terraform em `infra/` cujo `main.tf` apenas instancia módulos e liga outputs a inputs, sem declarar recursos AWS diretamente, exceto `random_password` e recursos de composição sem módulo próprio. Todo módulo SHALL viver em `infra/modules/<nome>/` com `main.tf`, `variables.tf`, `outputs.tf` e `versions.tf`, sem referenciar outro módulo por caminho relativo (composição só pela raiz).

#### Scenario: Módulo auto-contido
- **WHEN** um módulo em `infra/modules/<nome>/` é validado isoladamente com `terraform init -backend=false && terraform validate`
- **THEN** a validação passa sem depender de arquivos fora do diretório do módulo

#### Scenario: Raiz sem recursos soltos
- **WHEN** `infra/main.tf` é inspecionado
- **THEN** contém somente blocos `module`, `random_password` e `locals` de nomenclatura

### Requirement: Ambientes dev e prod com state isolado
O sistema SHALL suportar os ambientes `dev` e `prod` a partir da mesma raiz, diferenciados por `infra/envs/<env>/terraform.tfvars` e `infra/envs/<env>/backend.hcl`. Cada ambiente SHALL ter state próprio em bucket S3 com `use_lockfile = true`, criptografia SSE e chave distinta por ambiente. Região SHALL ser `us-east-2`.

#### Scenario: Init por ambiente
- **WHEN** `terraform init -backend-config=envs/dev/backend.hcl` é executado
- **THEN** o state é armazenado na chave do ambiente `dev`, sem tocar o state de `prod`

#### Scenario: Diferenças de dimensionamento vêm só de tfvars
- **WHEN** `terraform plan -var-file=envs/prod/terraform.tfvars` é comparado com `envs/dev/terraform.tfvars`
- **THEN** as diferenças (classe do RDS, Multi-AZ, `desired_count`, `deletion_protection`, `recovery_window_in_days`) vêm exclusivamente de variáveis, sem `if` por nome de ambiente em módulos

### Requirement: Nomenclatura determinística
Recursos SHALL usar o prefixo `rag-<env>` (`env ∈ {dev, prod}`). As roles ECS SHALL se chamar exatamente `rag-<env>-exec` e `rag-<env>-task`, e o secret `rag/<env>/app`, porque a role OIDC do GitHub Actions (criada manualmente) referencia esses nomes em `iam:PassRole`.

#### Scenario: Nome previsível de role
- **WHEN** o ambiente `dev` é aplicado
- **THEN** existem as roles `rag-dev-exec` e `rag-dev-task`, e nenhuma outra role ECS de aplicação

### Requirement: Tags padrão
Todos os recursos taggeáveis SHALL receber `default_tags` do provider com `Project=rag-enterprise`, `Environment=<env>` e `ManagedBy=terraform`.

#### Scenario: Tag em recurso criado
- **WHEN** qualquer recurso taggeável é criado
- **THEN** possui as três tags padrão

### Requirement: Gate de destroy
`terraform destroy` SHALL só ser executado quando `infra/destroy_config.json` tiver `true` para o ambiente alvo, e nunca no mesmo fluxo do `apply` automático. Recursos com dado persistente SHALL ter proteção configurável por variável: RDS `deletion_protection` e `skip_final_snapshot`, buckets `force_destroy`, secret `recovery_window_in_days`.

#### Scenario: Prod protegido
- **WHEN** o ambiente `prod` é aplicado com o tfvars padrão
- **THEN** RDS tem `deletion_protection = true` e `skip_final_snapshot = false`, e buckets têm `force_destroy = false`

#### Scenario: Dev descartável
- **WHEN** o ambiente `dev` é aplicado com o tfvars padrão
- **THEN** RDS tem `deletion_protection = false` e `skip_final_snapshot = true`, e o secret tem `recovery_window_in_days = 0`

### Requirement: Contrato de outputs para o CI
A raiz SHALL exportar como outputs: `ecs_cluster_name`, `ecs_service_name`, `ecs_task_family`, `ecs_exec_role_arn`, `ecs_task_role_arn`, `frontend_bucket_name`, `cloudfront_distribution_id`, `cloudfront_domain`, `api_url`, `cognito_authority`, `cognito_client_id` e `cognito_user_pool_id`. Outputs que contenham segredo SHALL NOT ser exportados.

#### Scenario: CI lê outputs
- **WHEN** um workflow executa `terraform output -json` após o apply
- **THEN** todos os outputs do contrato estão presentes e nenhum contém senha, chave de API ou ARN de valor secreto

### Requirement: Bootstrap externo documentado
O bucket de state, a role OIDC `ghactions-rag-enterprise` e os repositórios ECR SHALL permanecer fora do Terraform. O repositório SHALL documentar em `infra/README.md` o bootstrap do bucket de state e as permissões exigidas pela role OIDC (ECR, ECS, S3 frontend, CloudFront, state, apply e `iam:PassRole` restrito às duas roles ECS com `iam:PassedToService = ecs-tasks.amazonaws.com`).

#### Scenario: Onboarding sem conhecimento tácito
- **WHEN** alguém novo segue `infra/README.md` do zero
- **THEN** consegue criar o bucket de state, preencher `backend.hcl` e rodar o primeiro `plan`
