package tech.buildrun.notebooklm.adapter.out.persistence;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import tech.buildrun.notebooklm.adapter.out.persistence.repository.OutboxJpaRepository;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxStatus;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry registry, OutboxJpaRepository repository) {
        Gauge.builder("outbox.pending", () -> repository.countByStatus(OutboxStatus.PENDING)).register(registry);
        Gauge.builder("outbox.oldest.pending.age.seconds", () -> {
            Instant oldest = repository.oldestPendingCreatedAt();
            return oldest == null ? 0 : Duration.between(oldest, Instant.now()).toSeconds();
        }).baseUnit("seconds").register(registry);
    }
}
