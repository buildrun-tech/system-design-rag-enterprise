package tech.buildrun.notebooklm.core.port.out.persistence;

import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;

import java.time.Instant;
import java.util.List;

public interface OutboxRepositoryPort {

    OutboxEvent save(OutboxEvent event);

    /** Reivindica eventos PENDING vencidos com lock exclusivo (SKIP LOCKED); exige transação ativa. */
    List<OutboxEvent> claimBatch(Instant now, int limit);

    int deleteSentBefore(Instant threshold);
}
