package tech.buildrun.notebooklm.core.usecase.outbox;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEvent;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxEventType;
import tech.buildrun.notebooklm.core.domain.outbox.OutboxStatus;
import tech.buildrun.notebooklm.core.port.out.MessagePublisherPort;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelayOutboxUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final OutboxRepositoryPort outbox = mock(OutboxRepositoryPort.class);
    private final MessagePublisherPort publisher = mock(MessagePublisherPort.class);
    private final RelayOutboxUseCase relay = new RelayOutboxUseCase(outbox, publisher,
            Clock.fixed(NOW, ZoneOffset.UTC), 10, 3, Duration.ofSeconds(2), Duration.ofSeconds(10));

    private OutboxEvent pending(int attempts) {
        OutboxEvent event = OutboxEvent.restore(UUID.randomUUID(), OutboxEventType.INGESTION_REQUESTED,
                UUID.randomUUID(), Map.of("k", "v"), OutboxStatus.PENDING, attempts, NOW, null, null);
        when(outbox.claimBatch(NOW, 10)).thenReturn(List.of(event));
        return event;
    }

    @Test
    void publishesAndMarksSent() {
        OutboxEvent event = pending(0);

        assertThat(relay.relay()).isEqualTo(1);

        assertThat(event.status()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.sentAt()).isEqualTo(NOW);
        verify(publisher).publish(event);
        verify(outbox).save(event);
    }

    @Test
    void failureSchedulesRetryWithExponentialBackoff() {
        OutboxEvent event = pending(1);
        doThrow(new IllegalStateException("sqs down")).when(publisher).publish(any());

        relay.relay();

        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.attempts()).isEqualTo(2);
        assertThat(event.nextAttemptAt()).isEqualTo(NOW.plusSeconds(4));
        assertThat(event.lastError()).isEqualTo("sqs down");
        verify(outbox).save(event);
    }

    @Test
    void backoffIsCapped() {
        OutboxEvent event = OutboxEvent.restore(UUID.randomUUID(), OutboxEventType.INGESTION_REQUESTED,
                UUID.randomUUID(), Map.of("k", "v"), OutboxStatus.PENDING, 1, NOW, null, null);
        RelayOutboxUseCase capped = new RelayOutboxUseCase(outbox, publisher, Clock.fixed(NOW, ZoneOffset.UTC), 10,
                100, Duration.ofSeconds(2), Duration.ofSeconds(3));
        when(outbox.claimBatch(NOW, 10)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("x")).when(publisher).publish(any());

        capped.relay();

        assertThat(event.nextAttemptAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void exhaustedAttemptsMarksFailed() {
        OutboxEvent event = pending(2);
        doThrow(new IllegalStateException("sqs down")).when(publisher).publish(any());

        relay.relay();

        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.attempts()).isEqualTo(3);
    }

    @Test
    void oneFailureDoesNotStopTheBatch() {
        OutboxEvent first = OutboxEvent.restore(UUID.randomUUID(), OutboxEventType.INGESTION_REQUESTED,
                UUID.randomUUID(), Map.of("k", "v"), OutboxStatus.PENDING, 0, NOW, null, null);
        OutboxEvent second = OutboxEvent.restore(UUID.randomUUID(), OutboxEventType.INGESTION_REQUESTED,
                UUID.randomUUID(), Map.of("k", "v"), OutboxStatus.PENDING, 0, NOW, null, null);
        when(outbox.claimBatch(NOW, 10)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("x")).when(publisher).publish(first);

        relay.relay();

        assertThat(first.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(second.status()).isEqualTo(OutboxStatus.SENT);
    }
}
