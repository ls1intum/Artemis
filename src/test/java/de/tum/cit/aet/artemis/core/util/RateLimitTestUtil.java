package de.tum.cit.aet.artemis.core.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.admin.service.RateLimitConfigurationService;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.config.RateLimitConfig;
import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinuteAspect;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;

/**
 * Runs a test action with a rate limiter that has its own, usually tiny, limits and buckets, so that the limits of {@code @LimitRequestsPerMinute} can be tested without
 * affecting other tests.
 */
public final class RateLimitTestUtil {

    private RateLimitTestUtil() {
    }

    /**
     * An action of a test that may throw.
     */
    @FunctionalInterface
    public interface ThrowingAction {

        void run() throws Exception;
    }

    /**
     * Runs the action with a fresh rate limiter configured by the given customizer and restores the original limiter afterwards.
     *
     * @param requestLimitAspect the aspect that enforces {@code @LimitRequestsPerMinute}
     * @param customizer         sets the limits of the temporary limiter
     * @param action             the action to run
     * @throws Exception if the action fails
     */
    public static void withRateLimits(LimitRequestsPerMinuteAspect requestLimitAspect, Consumer<RateLimitingProperties> customizer, ThrowingAction action) throws Exception {
        try (var ignored = useRateLimits(requestLimitAspect, customizer)) {
            action.run();
        }
    }

    /**
     * Installs fresh rate-limit buckets until the returned scope is closed. Useful for per-test setup and teardown.
     *
     * @param requestLimitAspect the aspect enforcing request limits
     * @param customizer         configures the temporary limits
     * @return a scope restoring the original limiter
     */
    public static AutoCloseable useRateLimits(LimitRequestsPerMinuteAspect requestLimitAspect, Consumer<RateLimitingProperties> customizer) {
        var properties = new RateLimitingProperties();
        properties.setEnabled(true);
        customizer.accept(properties);
        var features = mock(FeatureToggleService.class);
        when(features.isFeatureEnabled(Feature.RateLimit)).thenReturn(true);
        var limiter = new RateLimitService(new RateLimitConfig().localRateLimitProxyManager(), new RateLimitConfigurationService(properties), features);
        Object aspectTarget = AopTestUtils.getUltimateTargetObject(requestLimitAspect);
        Object originalLimiter = ReflectionTestUtils.getField(aspectTarget, "rateLimitService");
        ReflectionTestUtils.setField(aspectTarget, "rateLimitService", limiter);
        return () -> ReflectionTestUtils.setField(aspectTarget, "rateLimitService", originalLimiter);
    }
}
