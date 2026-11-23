## Context

`UploadSourceUseCase.upload` roda dentro de `TransactionTemplate` (`SourceUseCaseConfig`) e chama `IngestionQueuePort.enqueue` → `SqsTemplate.send` síncrono antes do commit. Dual-write: mensagem pode chegar antes do dado visível, ficar órfã em rollback, ou o erro de SQS derruba o upload. Único produtor SQS hoje. Consumer (`SqsIngestionListener`) já é idempotente (`source.isReady()` → skip). Core sem Spring/JPA/AWS (ArchUnit). Backend roda em múltiplas tasks ECS.

## Goals / Non-Goals

**Goals:**
- Gravar evento na mesma tx do dado (atomicidade).
- Relay por polling `@Scheduled`, seguro com N instâncias via `FOR UPDATE SKIP LOCKED`.
- Status, retry com backoff, estado terminal `FAILED`, retenção.

**Non-Goals:**
- CDC/Debezium, FIFO/dedupe SQS, exactly-once.
- Framework genérico de handlers/tipos de evento (só `INGESTION_REQUESTED`).
- Lease/`PROCESSING` com recuperação (ver decisão 2).
- Replay manual de `FAILED` (follow-up).

## Decisions

1. **`IngestionQueuePort.enqueue` mantido; impl vira writer do outbox.** `UploadSourceUseCase` não muda; a tx existente cobre source + evento. Alternativa (nova porta `OutboxPort` no use case) rejeitada: mexe no core sem ganho.

2. **Tx do relay = claim + send + update (tx única, lote pequeno).** `SKIP LOCKED` segura o lock enquanto a tx vive; sem status `PROCESSING`/lease. Batch default 10, timeout no client SQS. Alternativa lease (`PROCESSING` + `locked_until` + recuperação): resiste a SQS lento, mas adiciona estado e job; só se conexão Hikari presa virar problema.

3. **Query nativa** `SELECT ... WHERE status='PENDING' AND next_attempt_at <= now() ORDER BY next_attempt_at LIMIT :n FOR UPDATE SKIP LOCKED`. Mais explícita que hint JPA `lock.timeout=-2`; permite ordenação controlada.

4. **Schema `tb_outbox`:** `id UUID`, `type VARCHAR`, `aggregate_id UUID`, `payload JSONB`, `status` CHECK (`PENDING`,`SENT`,`FAILED`), `attempts INT`, `next_attempt_at`, `last_error`, `created_at`, `sent_at`. Índice parcial `(next_attempt_at) WHERE status='PENDING'`. Índice `(sent_at) WHERE status='SENT'` para retenção.

5. **Payload carrega `requestId`/`userId` capturados no `enqueue`** (MDC não existe no thread do scheduler); relay restaura MDC antes de publicar para manter correlação nos logs.

6. **Hexagonal:** domínio `OutboxEvent` (sem setters; `markSent`, `markRetry(backoff)`, `markFailed`) + `OutboxStatus`; `OutboxRepositoryPort` (`save`, `claimBatch`, `deleteSentBefore`); `MessagePublisherPort` (SQS, adapter out); `RelayOutboxUseCase`; scheduler em `adapter/in/scheduling` só chama o use case. Relay em `TransactionTemplate` na config, como os demais.

7. **Backoff:** `next_attempt_at = now() + min(base * 2^attempts, cap)`. Props `outbox.poll-interval` (1s), `batch-size` (10), `max-attempts` (10), `backoff-base` (2s), `backoff-cap` (5min), `retention` (7d).

8. **Sem ShedLock:** `SKIP LOCKED` já evita duplo processamento entre instâncias; retenção é idempotente.

## Risks / Trade-offs

- [Chamada de rede dentro da tx do relay prende conexão Hikari] → batch pequeno, timeout SQS, monitorar pool; migrar para lease se necessário.
- [Duplicata se crash entre send e commit de `SENT`] → at-least-once aceito; consumer idempotente. Ressalva: duplicata chegando enquanto source está `PROCESSING` reexecuta ingestão (já ocorre hoje em reentrega SQS).
- [Crescimento da tabela] → job de retenção + índices parciais.
- [`FAILED` silencioso] → métrica/alarme de pendentes e idade; log ERROR ao falhar definitivamente.
- [Latência +0..1s no enfileiramento] → irrelevante frente à ingestão (segundos/minutos).
- [Testes existentes assumem send síncrono] → atualizar para assertar linha no outbox.

## Migration Plan

1. Deploy com `V6` (tabela vazia) + código novo. Flyway roda na subida.
2. Mensagens já enviadas pelo caminho antigo seguem normais; não há backfill.
3. Rollback: reverter a versão; eventos `PENDING` remanescentes ficam órfãos → antes do rollback, drenar (aguardar `outbox.pending = 0`) ou reenviar manualmente.

## Open Questions

- Replay de `FAILED` via endpoint/admin: fora do escopo agora?
- Alarme CloudWatch de outbox envelhecido entra nesta change ou em `service-observability`?
