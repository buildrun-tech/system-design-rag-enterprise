## Why

`UploadSourceUseCase` envia a mensagem SQS **dentro** da transação do banco (`SourceUseCaseConfig`). Dual-write: SQS pode entregar antes do commit (`SourceNotFoundException` → reentrega queimando `maxReceiveCount`), ou o commit falha após o `send` (mensagem órfã), ou o `send` falha e derruba o upload inteiro. Outbox pattern grava a intenção de envio na mesma transação do dado e um relay publica depois.

## What Changes

- Nova tabela `tb_outbox` (migration `V6`) com controle de status (`PENDING`/`SENT`/`FAILED`), `attempts`, `next_attempt_at`, `last_error`.
- `IngestionQueuePort.enqueue` passa a gravar evento no outbox (mesma tx do upload) em vez de chamar o SQS.
- Novo relay `@Scheduled` (polling) que reivindica lote com `SELECT ... FOR UPDATE SKIP LOCKED`, publica no SQS e marca `SENT`; falha → retry com backoff; estourou `max-attempts` → `FAILED`.
- `SKIP LOCKED` permite várias tasks ECS rodando o relay sem coordenação (sem ShedLock).
- Job de retenção remove eventos `SENT` antigos.
- Métricas `outbox.pending` e idade do evento mais antigo (encaixa em `service-observability`).
- Semântica at-least-once; consumer já é idempotente (`source.isReady()` → skip).

## Capabilities

### New Capabilities
- `transactional-outbox`: gravação atômica de eventos de saída, relay por polling com SKIP LOCKED, status, retry/backoff, retenção.

### Modified Capabilities
- `source-ingestion`: publicação na fila deixa de ser síncrona ao upload; mensagem é registrada no outbox na mesma transação e publicada de forma assíncrona.

## Impact

- Código: `UploadSourceUseCase` (praticamente intacto), `SqsIngestionQueueAdapter` (vira writer do outbox + novo publisher SQS), novos `OutboxEvent`, `OutboxRepositoryPort`, `RelayOutboxUseCase`, entity/repo JPA, scheduler em `adapter/in`, `@EnableScheduling`.
- Testes: ajustar `SourceUseCasesTest`, `SourceApiIntegrationTest`, `AdaptersTest`; novos testes do relay e de concorrência SKIP LOCKED (Testcontainers).
- DB: migration `V6`. Hikari: relay segura 1 conexão por poll.
- Infra: sem mudança obrigatória; opcional alarme de outbox envelhecido.
- Docs: `ARCHITECTURE.md`, `DOMAIN.md`.
- Latência upload→SQS passa a +0..poll-interval (irrelevante vs. tempo de ingestão).
