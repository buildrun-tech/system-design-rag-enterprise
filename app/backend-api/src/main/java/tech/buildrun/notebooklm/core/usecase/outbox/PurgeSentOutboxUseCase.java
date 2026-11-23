package tech.buildrun.notebooklm.core.usecase.outbox;

import tech.buildrun.notebooklm.core.port.in.PurgeOutboxPort;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;

import java.time.Clock;
import java.time.Duration;

public class PurgeSentOutboxUseCase implements PurgeOutboxPort {

    private final OutboxRepositoryPort outbox;
    private final Clock clock;
    private final Duration retention;

    public PurgeSentOutboxUseCase(OutboxRepositoryPort outbox, Clock clock, Duration retention) {
        this.outbox = outbox;
        this.clock = clock;
        this.retention = retention;
    }

    @Override
    public int purge() {
        return outbox.deleteSentBefore(clock.instant().minus(retention));
    }
}
