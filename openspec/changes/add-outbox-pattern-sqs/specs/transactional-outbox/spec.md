## ADDED Requirements

### Requirement: Registro atômico de evento de saída
O sistema SHALL gravar o evento de saída na tabela `tb_outbox` com `status: PENDING` na mesma transação que altera o estado de negócio que o originou.

#### Scenario: Commit grava dado e evento
- **WHEN** a transação do caso de uso é confirmada
- **THEN** o dado de negócio e o evento `PENDING` existem no banco

#### Scenario: Rollback descarta o evento
- **WHEN** a transação do caso de uso sofre rollback
- **THEN** nenhum evento é registrado e nenhuma mensagem é enviada ao SQS

### Requirement: Relay por polling com SKIP LOCKED
O sistema SHALL executar um relay agendado (`@Scheduled`) que seleciona lote de eventos `PENDING` com `next_attempt_at <= now()` usando `FOR UPDATE SKIP LOCKED`, de forma que múltiplas instâncias não processem o mesmo evento simultaneamente.

#### Scenario: Duas instâncias concorrentes
- **WHEN** dois relays executam o poll ao mesmo tempo sobre os mesmos eventos `PENDING`
- **THEN** cada evento é reivindicado por no máximo um relay
- **AND** nenhum relay bloqueia esperando o lock do outro

#### Scenario: Envio bem-sucedido
- **WHEN** o relay publica o evento no SQS com sucesso
- **THEN** o evento passa a `SENT` com `sent_at` preenchido

### Requirement: Retry com backoff e estado terminal
O sistema SHALL incrementar `attempts`, registrar `last_error` e reagendar `next_attempt_at` com backoff quando o envio falhar, e marcar o evento `FAILED` ao atingir `max-attempts`.

#### Scenario: Falha transitória no SQS
- **WHEN** o envio falha e `attempts < max-attempts`
- **THEN** o evento permanece `PENDING` com `next_attempt_at` no futuro

#### Scenario: Tentativas esgotadas
- **WHEN** o envio falha e `attempts` atinge `max-attempts`
- **THEN** o evento passa a `FAILED` e não é mais selecionado pelo relay

### Requirement: Entrega at-least-once
O sistema SHALL garantir entrega ao menos uma vez; consumidores SHALL tolerar mensagens duplicadas.

#### Scenario: Crash após envio e antes do commit de SENT
- **WHEN** o relay falha entre o envio ao SQS e o commit do status
- **THEN** o evento permanece `PENDING` e é reenviado no poll seguinte

### Requirement: Retenção
O sistema SHALL remover periodicamente eventos `SENT` mais antigos que o período de retenção configurado.

#### Scenario: Limpeza
- **WHEN** o job de retenção executa
- **THEN** eventos `SENT` com `sent_at` anterior ao limite são removidos
- **AND** eventos `PENDING` e `FAILED` são preservados

### Requirement: Observabilidade do outbox
O sistema SHALL expor métricas de quantidade de eventos `PENDING` e idade do evento pendente mais antigo.

#### Scenario: Backlog visível
- **WHEN** existem eventos `PENDING`
- **THEN** as métricas `outbox.pending` e idade do mais antigo refletem o backlog
