package de.tum.cit.aet.artemis.core.security.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.core.security.RateLimitType;

/**
 * This annotation is used to limit the number of requests per minute for a specific endpoint or controller.
 * It can be used to prevent abuse and ensure fair usage of resources.
 * The type specifies which rate limit category to apply, with RPM values configurable via application properties.
 *
 * <p>
 * Use {@link #type()} to specify a predefined rate limit type, which allows for configuration-based overrides
 * of the default RPM values defined in {@link RateLimitType}.
 * </p>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface LimitRequestsPerMinute {

    /**
     * Rate limit type that defines the default RPM and allows configuration overrides.
     *
     * @return the rate limit type
     */
    RateLimitType type() default RateLimitType.AUTHENTICATION;
}
