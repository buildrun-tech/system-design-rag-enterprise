package tech.buildrun.notebooklm.adapter.in.web.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.buildrun.notebooklm.core.domain.user.User;

import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class ControllerLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerLoggingAspect.class);
    private static final int MAX_LEN = 1000;

    @Around("within(tech.buildrun.notebooklm.adapter.in.web.controller..*)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        var sig = (MethodSignature) pjp.getSignature();
        String name = sig.getDeclaringType().getSimpleName() + "." + sig.getName();

        Map<String, Object> args = new LinkedHashMap<>();
        String[] names = sig.getParameterNames();
        Object[] values = pjp.getArgs();
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v instanceof User || v instanceof Jwt) continue;
            args.put(names[i], v instanceof MultipartFile f
                    ? "file(name=%s, size=%d, type=%s)".formatted(f.getOriginalFilename(), f.getSize(), f.getContentType())
                    : v instanceof SseEmitter ? "SseEmitter" : v);
        }
        log.info("Controller in: {} args={}", name, cut(args));

        long start = System.nanoTime();
        Object result = pjp.proceed();
        Object body = result instanceof ResponseEntity<?> r ? r.getBody() : result;
        log.info("Controller out: {} result={} durationMs={}", name,
                body instanceof SseEmitter ? "SseEmitter(stream)" : cut(body), (System.nanoTime() - start) / 1_000_000);
        return result;
    }

    private static String cut(Object o) {
        String s = String.valueOf(o);
        return s.length() <= MAX_LEN ? s : s.substring(0, MAX_LEN) + "...(truncated)";
    }
}
