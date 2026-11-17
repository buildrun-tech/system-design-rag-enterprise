package tech.buildrun.notebooklm.adapter.in.web.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private List<ILoggingEvent> run(String uri, int status) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", uri), response, (req, res) -> response.setStatus(status));
        return appender.list;
    }

    @Test
    void actuatorSuccessIsNotLogged() throws Exception {
        assertThat(run("/actuator/health", 200)).isEmpty();
    }

    @Test
    void actuatorServerErrorIsLogged() throws Exception {
        var events = run("/actuator/health", 503);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFormattedMessage()).isEqualTo("Request end");
        assertThat(events.get(0).getLevel().toString()).isEqualTo("ERROR");
    }

    @Test
    void apiRequestLogsOnlyEndWithKeyValues() throws Exception {
        var events = run("/api/notebooks", 200);
        assertThat(events).extracting(ILoggingEvent::getFormattedMessage).containsExactly("Request end");
        assertThat(events.get(0).getKeyValuePairs()).extracting(kv -> kv.key)
                .contains("method", "path", "route", "status", "durationMs");
    }

    private Object routeOf(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().filter(kv -> kv.key.equals("route")).findFirst().orElseThrow().value;
    }

    @Test
    void routeIsTheMatchedTemplate() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/notebooks/" + java.util.UUID.randomUUID());
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/api/v1/notebooks/{id}");
            response.setStatus(200);
        });
        assertThat(routeOf(appender.list.get(0))).isEqualTo("/api/v1/notebooks/{id}");
    }

    @Test
    void routeIsUnmatchedWithoutHandler() throws Exception {
        assertThat(routeOf(run("/nope", 404).get(0))).isEqualTo("unmatched");
    }
}
