## ADDED Requirements

### Requirement: ALB interno
O módulo `alb` SHALL criar um Application Load Balancer com `internal = true` nas subnets públicas, SG próprio sem regras e um listener HTTP :80 cuja ação padrão é resposta fixa `404`. SHALL exportar `alb_arn`, `alb_sg_id` e `listener_arn`. O ALB SHALL NOT ter listener HTTPS (TLS termina no API Gateway; sem ACM).

#### Scenario: ALB sem IP público
- **WHEN** o ALB é criado
- **THEN** seus endereços são somente IPs privados da VPC

#### Scenario: Rota desconhecida
- **WHEN** uma requisição chega no listener sem regra correspondente
- **THEN** o ALB responde `404`

### Requirement: Cluster ECS
O módulo `ecs-cluster` SHALL criar um cluster ECS com Container Insights configurável (padrão `disabled` em dev) e capacity providers `FARGATE`. SHALL exportar `cluster_arn` e `cluster_name`. O módulo SHALL ser reutilizável por mais de um service.

#### Scenario: Cluster compartilhável
- **WHEN** dois módulos `ecs-service` recebem o mesmo `cluster_arn`
- **THEN** ambos são criados no mesmo cluster sem conflito

### Requirement: ECS service com task definition bootstrap
O módulo `ecs-service` SHALL criar: log group, SG, target group (tipo `ip`, health check configurável), listener rule no `listener_arn` recebido, roles exec e task, task definition Fargate inicial e o `aws_ecs_service`. O `aws_ecs_service` SHALL declarar `lifecycle { ignore_changes = [task_definition, desired_count] }` para que revisões registradas e o desired count definido pelo CI não sejam revertidos. O primeiro apply SHALL usar `desired_count = 0`. Tasks SHALL usar `assign_public_ip = true` em subnets públicas e o deployment circuit breaker com rollback habilitado.

#### Scenario: Apply sem imagem publicada
- **WHEN** `terraform apply` roda antes de existir imagem no ECR
- **THEN** o service é criado com `desired_count = 0` e nenhum task tenta puxar imagem

#### Scenario: Terraform não desfaz deploy do CI
- **WHEN** o CI registra nova revisão e faz `update-service` e depois `terraform plan` roda
- **THEN** o plan não propõe alterar `task_definition` nem `desired_count` do service

#### Scenario: CI clona a revisão vigente
- **WHEN** o CI executa `describe-task-definition` da família e troca somente o campo `image`
- **THEN** variáveis de ambiente e `secrets` definidos pelo Terraform são preservados na nova revisão

### Requirement: Injeção de configuração e segredos na task
A task definition bootstrap SHALL injetar, via `secrets` com `valueFrom = "<secret_arn>:<chave>::"`, `SPRING_DATASOURCE_USERNAME` (`username`), `SPRING_DATASOURCE_PASSWORD` (`password`) e `SPRING_AI_OPENAI_API_KEY` (`openrouter_api_key`). SHALL injetar como `environment` plain: `SPRING_DATASOURCE_URL` (montada com host/port/dbname), `AWS_REGION`, `S3_BUCKET_NAME`, `SQS_INGESTION_QUEUE_URL`, `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `APP_CORS_ALLOWED_ORIGINS` (domínio CloudFront) e as variáveis `SPRING_AI_OPENAI_*` não secretas. Nenhum valor secreto SHALL aparecer em `environment`.

#### Scenario: Segredo fora do texto da task definition
- **WHEN** `describe-task-definition` é executado
- **THEN** senha do banco e chave do OpenRouter aparecem apenas como referência `valueFrom` ao secret

#### Scenario: CORS aponta pro frontend
- **WHEN** a task sobe
- **THEN** `APP_CORS_ALLOWED_ORIGINS` contém `https://<cloudfront_domain>` e nenhum `*`

### Requirement: Task execution role
A role `rag-<env>-exec` SHALL ter trust `ecs-tasks.amazonaws.com` com condições `aws:SourceAccount = <account>` e `ArnLike aws:SourceArn = arn:aws:ecs:us-east-2:<account>:*`. SHALL anexar `AmazonECSTaskExecutionRolePolicy` e uma policy inline com `secretsmanager:GetSecretValue` restrito ao ARN do secret único da aplicação.

#### Scenario: Leitura só do secret da app
- **WHEN** a exec role tenta ler outro secret da conta
- **THEN** recebe `AccessDenied`

#### Scenario: Trust restrito
- **WHEN** outro serviço AWS tenta assumir `rag-<env>-exec`
- **THEN** o `sts:AssumeRole` é negado

### Requirement: Task role de runtime
A role `rag-<env>-task` SHALL ter o mesmo trust da exec role. Suas permissões SHALL ser montadas pela raiz e passadas ao módulo como policy JSON: `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject` em `arn:<bucket sources>/*`; `s3:ListBucket` no bucket `sources`; `sqs:SendMessage`, `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:ChangeMessageVisibility`, `sqs:GetQueueAttributes` na ingest-queue. A task role SHALL NOT ter acesso a Secrets Manager, Bedrock, Cognito nem ao bucket `frontend`.

#### Scenario: Upload de source
- **WHEN** a app grava `{userId}/{notebookId}/{sourceId}/{filename}` no bucket `sources`
- **THEN** a operação é permitida

#### Scenario: Escrita no frontend negada
- **WHEN** a app tenta gravar no bucket `frontend`
- **THEN** recebe `AccessDenied`

#### Scenario: Publicação e consumo da fila
- **WHEN** a app publica e consome mensagens da ingest-queue
- **THEN** as operações são permitidas e o acesso à DLQ não é concedido

### Requirement: Permissões exigidas da role OIDC do GitHub
A documentação SHALL listar as permissões que a role `ghactions-rag-enterprise` precisa para o fluxo: push/pull no ECR; `ecs:DescribeTaskDefinition`, `ecs:RegisterTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeServices`; `iam:PassRole` **somente** em `rag-<env>-exec` e `rag-<env>-task` com `iam:PassedToService = ecs-tasks.amazonaws.com`; `s3` sync no bucket `frontend`; `cloudfront:CreateInvalidation`; leitura/escrita do state; e permissões de criação dos recursos do módulo para `apply`.

#### Scenario: PassRole restrito
- **WHEN** o CI registra uma task definition referenciando role diferente das duas roles do ambiente
- **THEN** o `iam:PassRole` é negado

### Requirement: Pré-requisitos da aplicação registrados
O `design.md` SHALL registrar os pré-requisitos da app para funcionar em ECS: profile `prod` sem defaults `test`/`localhost:4566` para credenciais e endpoint AWS (para a task role ser usada), e endpoint de health sem JWT para o health check do target group. A ausência desses itens SHALL ser tratada como bloqueio de deploy, não de provisionamento.

#### Scenario: Provisionar sem app pronta
- **WHEN** `terraform apply` roda antes dos ajustes no backend
- **THEN** a infra é criada com sucesso e o service permanece com `desired_count = 0`
