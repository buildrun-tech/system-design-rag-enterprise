package tech.buildrun.notebooklm.tool;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeToolsTest {

    @Test
    void currentDateTimeReturnsSaoPauloTime() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T21:30:00Z"), ZoneId.of("America/Sao_Paulo"));

        assertThat(new DateTimeTools(clock).currentDateTime()).isEqualTo("2026-10-19T18:30-03:00[America/Sao_Paulo]");
    }

    @Test
    void defaultConstructorUsesSaoPauloZone() {
        assertThat(new DateTimeTools().currentDateTime()).contains("[America/Sao_Paulo]");
    }
}
