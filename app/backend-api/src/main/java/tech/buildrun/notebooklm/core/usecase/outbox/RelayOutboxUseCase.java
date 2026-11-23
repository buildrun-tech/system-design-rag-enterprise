package tech.buildrun.notebooklm.core.usecase.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;
import tech.buildrun.notebooklm.core.port.in.RelayOutboxPort;
import tech.buildrun.notebooklm.core.port.out.MessagePublisherPort;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class RelayOutboxUseCase implements RelayOutboxPort {

    private static final Logger log = LoggerFactory.getLogger(RelayOutboxUseCase.class);

    private final OutboxRepositoryPort outbox;
    private final MessagePublisherPort publisher;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration backoffBase;
    private final Duration backoffCap;

    public RelayOutboxUseCase(OutboxRepositoryPort outbox, MessagePublisherPort publisher, Clock clock,
                              int batchSize, int maxAttempts, Duration backoffBase, Duration backoffCap) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.backoffBase = backoffBase;
        this.backoffCap = backoffCap;
    }

    /** Deve rodar em uma única transação: claim, publicação e atualização de status. */
    @Override
    public int relay() {
        List<OutboxEvent> batch = outbox.claimBatch(clock.instant(), batchSize);
        for (OutboxEvent event : batch) {
            publish(event);
        }
        return batch.size();
    }

    private void publish(OutboxEvent event) {
        try {
            publisher.publish(event);
            event.markSent(clock.instant());
            log.atInfo().setMessage("Outbox event sent").addKeyValue("outboxId", event.id())
                    .addKeyValue("aggregateId", event.aggregateId()).addKeyValue("attempts", event.attempts()).log();
        } catch (RuntimeException e) {
            registerFailure(event, e);
        }
        outbox.save(event);
    }

    private void registerFailure(OutboxEvent event, RuntimeException error) {
        String message = String.valueOf(error.getMessage());
        if (event.attempts() + 1 >= maxAttempts) {
            event.markFailed(message);
            log.atError().setMessage("Outbox event failed permanently").addKeyValue("outboxId", event.id())
                    .addKeyValue("aggregateId", event.aggregateId()).addKeyValue("attempts", event.attempts())
                    .setCause(error).log();
            return;
        }
        event.markRetry(message, clock.instant(), backoff(event.attempts()));
        log.atWarn().setMessage("Outbox publish failed, will retry").addKeyValue("outboxId", event.id())
                .addKeyValue("attempts", event.attempts()).setCause(error).log();
    }

    private Duration backoff(int attempts) {
        long factor = 1L << Math.min(attempts, 20);
        Duration delay = backoffBase.multipliedBy(factor);
        return delay.compareTo(backoffCap) > 0 ? backoffCap : delay;
    }
}
