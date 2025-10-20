package de.tum.cit.aet.artemis.core.security.annotations;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.core.service.RateLimitService;
import inet.ipaddr.IPAddress;

/**
 * Aspect that intercepts methods annotated with {@link LimitRequestsPerMinute} to enforce rate limiting.
 *
 * <p>
 * This aspect uses {@code @Before} advice to check rate limits before method execution.
 * If the rate limit is exceeded, a {@link de.tum.cit.aet.artemis.core.exception.RateLimitExceededException}
 * is thrown, preventing the method from executing.
 * </p>
 *
 * <p>
 * The aspect supports both fixed RPM values and configurable rate limit types,
 * allowing for flexible rate limiting strategies across different endpoint categories.
 * </p>
 */
@Profile(PROFILE_CORE)
@Component
@Aspect
@Lazy
public class LimitRequestsPerMinuteAspect {

    private final RateLimitService rateLimitService;

    private final RateLimitConfigurationService configurationService;

    public LimitRequestsPerMinuteAspect(RateLimitService rateLimitService, RateLimitConfigurationService configurationService) {
        this.rateLimitService = rateLimitService;
        this.configurationService = configurationService;
    }

    /**
     * Intercepts method calls to enforce rate limiting before method execution.
     *
     * <p>
     * This advice runs before the target method and will throw an exception
     * if the rate limit is exceeded, preventing the method from executing.
     * </p>
     *
     * @param joinPoint the join point representing the intercepted method call
     * @throws Throwable if rate limit is exceeded or other errors occur
     */
    @Before("@annotation(LimitRequestsPerMinute) || @within(LimitRequestsPerMinute)")
    public void checkRateLimit(JoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        LimitRequestsPerMinute annotation = method.getAnnotation(LimitRequestsPerMinute.class);

        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(LimitRequestsPerMinute.class);
        }

        if (annotation == null) {
            return;
        }

        IPAddress clientId = rateLimitService.resolveClientId();

        rateLimitService.enforcePerMinute(clientId, annotation.type());
    }
}
