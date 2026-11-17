package tech.buildrun.notebooklm.adapter.in.web.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Só identificadores (UUID) entram no log: corpo, texto de mensagem e nome de arquivo são conteúdo do usuário. */
@Aspect
@Component
public class ControllerLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ControllerLoggingAspect.class);

    @Around("within(tech.buildrun.notebooklm.adapter.in.web.controller..*)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        var sig = (MethodSignature) pjp.getSignature();
        Map<String, Object> ids = new LinkedHashMap<>();
        String[] names = sig.getParameterNames();
        Object[] values = pjp.getArgs();
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof UUID) ids.put(names[i], values[i]);
        }
        log.atInfo().setMessage("Controller in")
                .addKeyValue("controller", sig.getDeclaringType().getSimpleName() + "." + sig.getName())
                .addKeyValue("ids", ids).log();
        return pjp.proceed();
    }
}
