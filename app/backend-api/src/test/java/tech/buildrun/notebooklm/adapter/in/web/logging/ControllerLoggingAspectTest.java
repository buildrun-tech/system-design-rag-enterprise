package tech.buildrun.notebooklm.adapter.in.web.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerLoggingAspectTest {

    @Test
    void logsOnlyIdentifiersNeverUserContent() throws Throwable {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        var logger = (Logger) LoggerFactory.getLogger(ControllerLoggingAspect.class);
        logger.addAppender(appender);
        try {
            var sig = mock(MethodSignature.class);
            when(sig.getDeclaringType()).thenReturn((Class) SampleController.class);
            when(sig.getName()).thenReturn("create");
            when(sig.getParameterNames()).thenReturn(new String[]{"conversationId", "content"});
            var pjp = mock(ProceedingJoinPoint.class);
            when(pjp.getSignature()).thenReturn(sig);
            UUID id = UUID.randomUUID();
            when(pjp.getArgs()).thenReturn(new Object[]{id, "pergunta secreta do usuario"});
            when(pjp.proceed()).thenReturn("ok");

            assertThat(new ControllerLoggingAspect().around(pjp)).isEqualTo("ok");

            assertThat(appender.list).hasSize(1);
            var event = appender.list.get(0);
            assertThat(event.getFormattedMessage()).isEqualTo("Controller in");
            assertThat(event.getKeyValuePairs().toString()).contains(id.toString()).doesNotContain("secreta");
        } finally {
            logger.detachAppender(appender);
        }
    }

    static class SampleController {
    }
}
