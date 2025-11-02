package de.tum.cit.aet.artemis.core.security.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Indicates that the annotated element is intended for internal use only and should not be exposed to external clients.
 * The access to methods or classes annotated with @Internal is restricted to IP addresses defined in {@link de.tum.cit.aet.artemis.core.config.InternalAccessConfiguration}.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("permitAll()")
public @interface Internal {
}
