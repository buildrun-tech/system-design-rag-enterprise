## 1. Banco

- [x] 1.1 Criar `V6__add_outbox.sql` com `tb_outbox`, CHECK de status e índices parciais (`PENDING` por `next_attempt_at`, `SENT` por `sent_at`)
- [x] 1.2 Atualizar DDL em `DOMAIN.md`

## 2. Core

- [x] 2.1 Criar `OutboxStatus` e `OutboxEvent` (sem setters; `markSent`, `markRetry`, `markFailed`) + teste de domínio
- [x] 2.2 Criar `OutboxRepositoryPort` (`save`, `claimBatch`, `deleteSentBefore`) e `MessagePublisherPort`
- [x] 2.3 Criar `RelayOutboxUseCase` (claim, publish, sucesso/retry/backoff/`FAILED`) + teste unitário
- [x] 2.4 Criar caso de uso de retenção (`PurgeSentOutboxUseCase`) + teste

## 3. Adapters

- [x] 3.1 Criar `OutboxEntity`, Spring Data repository com query nativa `FOR UPDATE SKIP LOCKED` e `OutboxRepositoryAdapter`
- [x] 3.2 Reescrever `SqsIngestionQueueAdapter.enqueue` para gravar evento no outbox (payload com `sourceId`, `requestId`, `userId`)
- [x] 3.3 Criar publisher SQS (`MessagePublisherPort`) reutilizando `ExternalCallLog` e `SqsTemplate`, restaurando MDC do payload
- [x] 3.4 Criar `OutboxRelayScheduler` e `OutboxRetentionScheduler` em `adapter/in/scheduling` (`@Scheduled`)

## 4. Configuração

- [x] 4.1 Adicionar `@EnableScheduling` e props `outbox.*` em `application.yml`
- [x] 4.2 Registrar beans dos use cases com `TransactionTemplate` na config
- [x] 4.3 Configurar timeout do client SQS

## 5. Observabilidade

- [x] 5.1 Expor métricas `outbox.pending` e idade do evento pendente mais antigo
- [x] 5.2 Logar ERROR ao marcar evento `FAILED`

## 6. Testes

- [x] 6.1 Ajustar `SourceUseCasesTest`, `SourceApiIntegrationTest` e `AdaptersTest` (assertar linha no outbox)
- [x] 6.2 Teste de integração (Testcontainers): dois relays concorrentes não processam o mesmo evento
- [x] 6.3 Teste de integração: rollback do upload não deixa evento; SQS indisponível mantém `PENDING` e retenta
- [ ] 6.4 Rodar `HexagonalArchitectureTest` e quality gate Java

## 7. Documentação

- [x] 7.1 Atualizar `ARCHITECTURE.md` (fluxo de ingestão com outbox)
- [x] 7.2 Validar com `openspec validate add-outbox-pattern-sqs`

## 8. Dashboard CloudWatch

- [x] 8.1 Log "Outbox event sent" no relay e metric filters `OutboxSent`/`OutboxRetry`/`OutboxFailed` em `alarms.tf`
- [x] 8.2 Alarme `outbox-failed` (evento esgotou tentativas)
- [x] 8.3 Seção "Outbox" no dashboard: série temporal, totais da última hora, tabela por resultado e últimas falhas
