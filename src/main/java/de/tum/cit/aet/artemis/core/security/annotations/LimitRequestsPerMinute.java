package de.tum.cit.aet.artemis.core.security.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.core.security.RateLimitType;

/**
 * This annotation is used to limit the number of requests per minute for a specific endpoint or controller.
 * It can be used to prevent abuse and ensure fair usage of resources.
 * The value specifies the maximum number of requests for all nodes allowed per minute per client.
 *
 * <p>
 * You can either specify a fixed RPM value using {@link #value()} or use a predefined type
 * with {@link #type()} which allows for configuration-based overrides.
 * </p>
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface LimitRequestsPerMinute {

    /**
     * Maximum requests per minute per client for the annotated endpoint/controller.
     * If both value and type are specified, value takes precedence.
     *
     * @return the fixed RPM value, or -1 if using type-based configuration
     */
    int value() default -1;

    /**
     * Rate limit type that defines the default RPM and allows configuration overrides.
     * If both value and type are specified, value takes precedence.
     *
     * @return the rate limit type, or null if using fixed value
     */
    RateLimitType type() default RateLimitType.PUBLIC;
}
