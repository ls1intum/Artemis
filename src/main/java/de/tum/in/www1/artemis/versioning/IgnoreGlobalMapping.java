package de.tum.in.www1.artemis.versioning;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Informs the global request mapper to not apply global settings
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreGlobalMapping {

    /**
     * @return true if the path should not be modified to fit the global standard
     */
    boolean ignorePaths() default true;

    /**
     * @return true if the method should serve all (no annotations) or multiple HTTP methods
     */
    boolean ignoreUniqueMethods() default false;

    /**
     * Use with caution! Typically only the fallback-endpoint mapping the client need this.
     *
     * @return true if any collision should be ignored.
     */
    boolean ignoreCollision() default false;
}
