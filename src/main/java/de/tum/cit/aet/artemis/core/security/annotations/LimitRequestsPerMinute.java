package de.tum.cit.aet.artemis.core.security.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to limit the number of requests per minute for a specific endpoint or controller.
 * It can be used to prevent abuse and ensure fair usage of resources.
 * The value specifies the maximum number of requests for all nodes allowed per minute per client.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface LimitRequestsPerMinute {

    /**
     * Maximum requests per minute per client for the annotated endpoint/controller.
     */
    int value();
}
