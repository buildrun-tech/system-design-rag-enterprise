## ADDED Requirements

### Requirement: Dashboard de golden signals
O Terraform SHALL criar um dashboard CloudWatch com tráfego, latência (p50/p95/p99), erros (4xx/5xx) e saturação (CPU/memória do ECS, conexões e CPU do RDS) do backend.

#### Scenario: Dashboard provisionado
- **WHEN** `terraform apply` roda com o módulo `observability`
- **THEN** existe um dashboard com os widgets de tráfego, latência, erros e saturação

#### Scenario: Latência por rota
- **WHEN** o widget de latência é consultado
- **THEN** agrupa por `route` excluindo `OPTIONS`

### Requirement: Visão das integrações
O dashboard SHALL ter widgets para SQS (mensagens visíveis, idade da mais antiga, DLQ), S3 (4xx/5xx), RDS e erros por `integration` (OpenRouter, S3, SQS, pgvector).

#### Scenario: DLQ visível
- **WHEN** o dashboard é aberto
- **THEN** exibe `ApproximateNumberOfMessagesVisible` da DLQ

#### Scenario: Erros por integração
- **WHEN** há eventos com `outcome=error`
- **THEN** o widget mostra a contagem por `integration`

### Requirement: Visão do pipeline
O dashboard SHALL mostrar duração e sucesso/falha da ingestão (`Consumer out` e `Source ingestion failed`).

#### Scenario: Ingestão
- **WHEN** consumers terminam
- **THEN** o widget exibe contagem e duração média/máxima

### Requirement: Alarmes acionáveis
O Terraform SHALL criar alarmes com ação para um tópico SNS: DLQ visível maior que 0; 5xx do target maior que 1% por 5 minutos; idade da mensagem mais antiga da fila maior que 300 s; `HealthyHostCount` menor que `desired_count`; eventos `level=ERROR` maiores que 0 em 5 minutos; eventos `outcome=error` maiores que 0 em 5 minutos.

#### Scenario: Mensagem na DLQ
- **WHEN** uma mensagem chega na DLQ
- **THEN** o alarme da DLQ entra em ALARM e notifica o tópico SNS

#### Scenario: Erro no log
- **WHEN** o backend emite um log `ERROR`
- **THEN** o metric filter incrementa `ErrorCount` e o alarme correspondente dispara se o limiar for excedido

#### Scenario: Task fora do ar
- **WHEN** `HealthyHostCount` fica menor que `desired_count`
- **THEN** o alarme notifica o tópico SNS

### Requirement: Notificação configurável
O módulo SHALL receber `alert_email` e criar a subscription SNS correspondente.

#### Scenario: E-mail informado
- **WHEN** `alert_email` é definido
- **THEN** existe uma subscription de e-mail no tópico de alertas
