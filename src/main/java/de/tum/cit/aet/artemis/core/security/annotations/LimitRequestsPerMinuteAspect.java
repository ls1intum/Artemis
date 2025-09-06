package de.tum.cit.aet.artemis.core.security.annotations;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.RateLimitService;

@Aspect
@Component
public class LimitRequestsPerMinuteAspect {

    private final RateLimitService rateLimitService;

    public LimitRequestsPerMinuteAspect(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Around("@annotation(LimitRequestsPerMinute) || @within(LimitRequestsPerMinute)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        LimitRequestsPerMinute ann = method.getAnnotation(LimitRequestsPerMinute.class);
        if (ann == null) {
            ann = method.getDeclaringClass().getAnnotation(LimitRequestsPerMinute.class);
        }
        int rpm = ann.value();

        rateLimitService.enforcePerMinute(rateLimitService.resolveClientId(), rpm);

        return pjp.proceed();
    }
}
