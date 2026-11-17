package tech.buildrun.notebooklm.adapter.in.web.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : "unmatched";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        long start = System.nanoTime();
        boolean actuator = request.getRequestURI().startsWith("/actuator");
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            log.atError().setMessage("Request unhandled error").addKeyValue("method", request.getMethod())
                    .addKeyValue("path", request.getRequestURI()).setCause(e).log();
            throw e;
        } finally {
            int status = response.getStatus();
            if (!actuator || status >= 500) {
                Level level = status >= 500 ? Level.ERROR : status >= 400 ? Level.WARN : Level.INFO;
                log.atLevel(level).setMessage("Request end").addKeyValue("method", request.getMethod())
                        .addKeyValue("path", request.getRequestURI()).addKeyValue("route", route(request))
                        .addKeyValue("status", status)
                        .addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000).log();
            }
            MDC.clear();
        }
    }
}
