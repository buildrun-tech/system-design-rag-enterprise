## ADDED Requirements

### Requirement: VPC com subnets públicas e db isoladas, sem NAT
O módulo `network` SHALL criar uma VPC em 2 AZs com uma subnet pública e uma subnet `db` por AZ. Subnets públicas SHALL ter rota `0.0.0.0/0` para um Internet Gateway. Subnets `db` SHALL NOT ter rota para IGW nem para NAT. O módulo SHALL NOT criar NAT gateway nem VPC endpoints.

#### Scenario: Subnet db sem saída
- **WHEN** a route table associada às subnets `db` é inspecionada
- **THEN** contém somente a rota local da VPC

#### Scenario: Sem NAT
- **WHEN** o plan do ambiente é inspecionado
- **THEN** não existe nenhum recurso `aws_nat_gateway`, `aws_eip` de NAT ou `aws_vpc_endpoint`

#### Scenario: Outputs de rede
- **WHEN** o módulo `network` é aplicado
- **THEN** exporta `vpc_id`, `vpc_cidr`, `public_subnet_ids` e `db_subnet_ids`

### Requirement: Security groups nascem vazios no módulo dono
Os módulos `alb`, `ecs-service`, `rds-postgres` e `api-gateway-http` SHALL criar seu próprio security group sem nenhuma regra inline e SHALL exportar o `sg_id`. O módulo SHALL NOT depender de SG de outro módulo para ser criado. Todas as regras SHALL ser recursos `aws_vpc_security_group_ingress_rule` / `aws_vpc_security_group_egress_rule` separados, com origem/destino por referência de SG (nunca por CIDR de VPC), exceto o egress HTTPS das tasks.

#### Scenario: SG sem regras inline
- **WHEN** o `aws_security_group` de qualquer módulo é inspecionado
- **THEN** não declara blocos `ingress` ou `egress` inline

#### Scenario: Sem ciclo de dependência
- **WHEN** `terraform graph` é gerado para a raiz
- **THEN** não há ciclo entre `rds-postgres`, `alb`, `ecs-service` e `api-gateway-http`

### Requirement: Cadeia de SGs a partir do API Gateway
O tráfego permitido SHALL seguir exatamente `vpclink-sg → alb-sg → task-sg → db-sg`. As regras SHALL ser criadas pelo módulo consumidor:

- `api-gateway-http`: egress de `vpclink-sg` tcp 80 → `alb-sg`; ingress em `alb-sg` tcp 80 ← `vpclink-sg`.
- `ecs-service`: ingress em `task-sg` tcp `container_port` ← `alb-sg`; egress em `alb-sg` tcp `container_port` → `task-sg`; egress de `task-sg` tcp 5432 → `db-sg`; ingress em `db-sg` tcp 5432 ← `task-sg`; egress de `task-sg` tcp 443 → `0.0.0.0/0`.

`vpclink-sg` SHALL NOT ter ingress. `db-sg` SHALL NOT ter egress.

#### Scenario: Chamada via API Gateway chega na task
- **WHEN** um cliente chama a URL do API Gateway
- **THEN** a requisição atravessa VPC Link, ALB e task ECS, e cada salto é permitido por exatamente uma regra de SG da cadeia

#### Scenario: Acesso direto à task é bloqueado
- **WHEN** um host da internet tenta conectar no IP público de uma task ECS na porta do container
- **THEN** a conexão não é aceita, porque `task-sg` só permite ingress de `alb-sg`

#### Scenario: Acesso direto ao ALB é bloqueado
- **WHEN** qualquer origem tenta alcançar o ALB fora do VPC Link
- **THEN** não há rota (ALB `internal`) e `alb-sg` só admite `vpclink-sg`

#### Scenario: RDS acessível só pela task
- **WHEN** qualquer origem que não seja `task-sg` tenta conectar na porta 5432 do RDS
- **THEN** a conexão é rejeitada por `db-sg`

#### Scenario: RDS sem egress
- **WHEN** as regras de egress de `db-sg` são listadas
- **THEN** a lista está vazia

### Requirement: Egress das tasks limitado a HTTPS e ao banco
`task-sg` SHALL permitir egress somente tcp 443 para `0.0.0.0/0` (OpenRouter, JWKS do Cognito, ECR, S3, SQS, Secrets Manager, CloudWatch Logs) e tcp 5432 para `db-sg`. Tasks SHALL rodar em subnets públicas com `assign_public_ip = true`, necessário para saída sem NAT.

#### Scenario: Saída para OpenRouter
- **WHEN** a task chama o endpoint HTTPS do OpenRouter
- **THEN** a conexão sai pela regra tcp 443 e pelo IGW

#### Scenario: Saída em porta não prevista
- **WHEN** a task tenta conexão de saída em porta diferente de 443 ou 5432 (DB)
- **THEN** a conexão é bloqueada por `task-sg`
