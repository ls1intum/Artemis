package de.tum.in.www1.artemis.security.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * This annotation is used to enforce an endpoint using a file token authentication.
 * It should only be used with endpoints starting with {@code /api/file/}
 * <p>
 * It's only addable to methods. The intention is that a developer can see the required role without the need to scroll up.
 * This also prevents overrides of the annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("permitAll()")
public @interface EnforceFile {

}
