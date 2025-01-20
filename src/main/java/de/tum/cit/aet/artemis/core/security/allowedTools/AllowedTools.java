package de.tum.cit.aet.artemis.core.security.allowedTools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the tools allowed for certain methods or classes.
 * Used to restrict access or customize behavior based on the provided {@link ToolTokenType}.
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedTools {

    /**
     * Returns the allowed tools specified in the annotation of the method or class.
     *
     * @return the allowed tools specified by the annotation
     */
    ToolTokenType[] value();
}
