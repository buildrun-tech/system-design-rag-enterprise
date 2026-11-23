package tech.buildrun.notebooklm.core.usecase.outbox;

import org.junit.jupiter.api.Test;
import tech.buildrun.notebooklm.core.port.out.persistence.OutboxRepositoryPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurgeSentOutboxUseCaseTest {

    @Test
    void deletesSentOlderThanRetention() {
        Instant now = Instant.parse("2026-01-10T00:00:00Z");
        OutboxRepositoryPort outbox = mock(OutboxRepositoryPort.class);
        when(outbox.deleteSentBefore(Instant.parse("2026-01-03T00:00:00Z"))).thenReturn(5);

        int removed = new PurgeSentOutboxUseCase(outbox, Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(7)).purge();

        assertThat(removed).isEqualTo(5);
    }
}
