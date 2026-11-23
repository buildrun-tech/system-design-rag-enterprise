package tech.buildrun.notebooklm.core.domain;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEventType;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private OutboxEvent event() {
        return OutboxEvent.create(OutboxEventType.INGESTION_REQUESTED, UUID.randomUUID(), Map.of("k", "v"), NOW);
    }

    @Test
    void startsPendingWithoutAttempts() {
        OutboxEvent event = event();

        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.attempts()).isZero();
        assertThat(event.nextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void markSentSetsStatusAndTimestamp() {
        OutboxEvent event = event();

        event.markSent(NOW.plusSeconds(1));

        assertThat(event.status()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.sentAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void markRetryKeepsPendingAndSchedulesNextAttempt() {
        OutboxEvent event = event();

        event.markRetry("boom", NOW, Duration.ofSeconds(4));

        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.attempts()).isEqualTo(1);
        assertThat(event.lastError()).isEqualTo("boom");
        assertThat(event.nextAttemptAt()).isEqualTo(NOW.plusSeconds(4));
    }

    @Test
    void markFailedIsTerminal() {
        OutboxEvent event = event();

        event.markFailed("boom");

        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.attempts()).isEqualTo(1);
    }
}
