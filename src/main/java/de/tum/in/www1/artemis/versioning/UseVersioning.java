package de.tum.in.www1.artemis.versioning;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Marks an endpoint to be versioned.
 * <p>
 * In the future, we would like to remove this annotation and use versioning as a default, however, we want to make sure our endpoints are consistent and as REST-conform as
 * possible. In the meantime, we activate versioning for the verified endpoints using this annotation.
 * <p>
 * TODO: Remove this annotation and all occurrences of it once all endpoints are versioned. Also remove section in
 * {@link VersionRequestMappingHandlerMapping#getMappingForMethod(Method, Class)}.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface UseVersioning {
}
