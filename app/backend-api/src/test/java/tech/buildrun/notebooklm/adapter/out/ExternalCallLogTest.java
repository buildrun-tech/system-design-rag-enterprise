package tech.buildrun.notebooklm.adapter.out;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalCallLogTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExternalCallLog.class);

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private Map<String, Object> fields(ILoggingEvent e) {
        return e.getKeyValuePairs().stream().collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
    }

    @Test
    void okLogsOutcomeAndDuration() {
        String result = ExternalCallLog.measure("s3", "download", () -> "x");

        assertThat(result).isEqualTo("x");
        var event = appender.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("INFO");
        assertThat(fields(event)).containsEntry("integration", "s3").containsEntry("operation", "download")
                .containsEntry("outcome", "ok").containsKey("durationMs");
    }

    @Test
    void errorLogsAtErrorWithCauseAndRethrows() {
        var boom = new IllegalStateException("boom");

        assertThatThrownBy(() -> ExternalCallLog.run("sqs", "send", () -> { throw boom; })).isSameAs(boom);

        var event = appender.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(fields(event)).containsEntry("outcome", "error").containsEntry("integration", "sqs");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }
}
