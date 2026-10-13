package tech.buildrun.notebooklm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gera/propaga o requestId (header X-Request-Id), coloca no MDC e loga start/end de toda request.
 * Roda antes do Spring Security, entao cobre tambem 401/403.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        long start = System.nanoTime();
        log.info("Request start: {} {}", request.getMethod(), request.getRequestURI());
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            log.error("Request unhandled error: {} {}", request.getMethod(), request.getRequestURI(), e);
            throw e;
        } finally {
            int status = response.getStatus();
            String msg = "Request end: {} {} status={} durationMs={}";
            Object[] args = {request.getMethod(), request.getRequestURI(), status,
                    (System.nanoTime() - start) / 1_000_000};
            if (status >= 500) {
                log.error(msg, args);
            } else if (status >= 400) {
                log.warn(msg, args);
            } else {
                log.info(msg, args);
            }
            MDC.clear();
        }
    }
}
