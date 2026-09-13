package de.tum.cit.aet.artemis.core.security.annotations;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.Method;
import java.util.Optional;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.security.RateLimitKey;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
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
        LimitRequestsPerMinute annotation = resolveAnnotation(method, joinPoint.getTarget());

        if (annotation == null) {
            return;
        }

        IPAddress clientAddress = rateLimitService.resolveClientId();

        if (annotation.key() == RateLimitKey.USER) {
            Optional<String> login = SecurityUtils.getCurrentUserLogin();
            if (login.isPresent()) {
                // Count per user so that users sharing one source address (for example a campus network behind NAT)
                // do not drain a common budget. Exemption stays address-based, so the client address is still passed.
                rateLimitService.enforcePerMinute(clientAddress, "user:" + login.get(), annotation.type());
                return;
            }
            // No authenticated principal (not expected on endpoints guarded by @EnforceAtLeastStudent): fall back to
            // counting per client address so the endpoint is never left unlimited.
        }

        rateLimitService.enforcePerMinute(clientAddress, annotation.type());
    }

    /**
     * Resolves the annotation from the method, else the runtime target class, else the declaring class.
     * <p>
     * Checking the runtime target class covers a class-level annotation on the concrete controller even when the
     * intercepted method is declared on a superclass, so a class-level limit cannot be silently missed.
     *
     * @param method the intercepted method
     * @param target the target object of the intercepted call (may be {@code null})
     * @return the resolved annotation, or {@code null} if none applies
     */
    private static LimitRequestsPerMinute resolveAnnotation(Method method, Object target) {
        LimitRequestsPerMinute annotation = method.getAnnotation(LimitRequestsPerMinute.class);
        if (annotation == null && target != null) {
            annotation = target.getClass().getAnnotation(LimitRequestsPerMinute.class);
        }
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(LimitRequestsPerMinute.class);
        }
        return annotation;
    }
}
