## ADDED Requirements

### Requirement: Terraform é dono da definição da task
O Terraform SHALL gerenciar a configuração da task definition do ECS (env vars, secrets, CPU, memória, roles, logs e health check), e uma mudança nessa configuração SHALL gerar uma nova revisão da família sem remover as revisões anteriores.

#### Scenario: Env var nova é aplicada
- **WHEN** uma variável de ambiente é adicionada ao módulo ECS e o `terraform apply` roda
- **THEN** uma nova revisão da família contém a variável e as revisões anteriores permanecem `ACTIVE`

### Requirement: Terraform apply não faz rollout
O `terraform apply` SHALL NOT alterar a task definition usada pelo ECS service nem a contagem desejada de tasks.

#### Scenario: Apply com mudança de configuração
- **WHEN** o `terraform apply` registra uma nova revisão da família
- **THEN** o service continua apontando para a revisão em que estava e nenhuma task é substituída

### Requirement: Deploy troca apenas a imagem
A action de deploy SHALL clonar a última revisão da família Terraform, alterar somente a imagem do container, registrar a nova revisão e apontar o service para ela.

#### Scenario: Deploy de imagem nova
- **WHEN** o CD roda com uma imagem nova
- **THEN** a revisão registrada tem a configuração da última revisão da família e apenas a imagem diferente, e o service passa a usá-la

#### Scenario: Configuração do Terraform mais nova que o service
- **WHEN** existe uma revisão do Terraform mais nova que a rodando no service e o CD faz deploy
- **THEN** a nova revisão inclui a configuração da revisão do Terraform

### Requirement: Deploy verifica convergência
A action de deploy SHALL aguardar o service estabilizar e SHALL falhar se o service não rodar a revisão registrada.

#### Scenario: Rollout não converge
- **WHEN** o service não roda a revisão registrada após a espera
- **THEN** o job falha com erro indicando a revisão esperada e a atual

### Requirement: Redeploy sem build
O repositório SHALL oferecer um workflow manual que promove a configuração mais recente da família usando a imagem que o service roda atualmente.

#### Scenario: Promover mudança de configuração
- **WHEN** o workflow de redeploy é disparado para um ambiente
- **THEN** uma revisão é registrada com a configuração mais recente do Terraform e a imagem atualmente em execução, e o service converge para ela
