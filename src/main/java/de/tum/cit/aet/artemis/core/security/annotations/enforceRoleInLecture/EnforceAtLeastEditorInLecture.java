package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.cit.aet.artemis.core.security.Role;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('EDITOR')")
@EnforceRoleInLecture(Role.EDITOR)
public @interface EnforceAtLeastEditorInLecture {

    /**
     * The name of the field in the method parameters that contains the lecture id.
     * This is used to extract the lecture id from the method parameters
     *
     * @return the name of the field in the method parameters that contains the lecture id
     */
    @AliasFor(annotation = EnforceRoleInLecture.class)
    String resourceIdFieldName() default "lectureId";

    /**
     * Optional descriptive name for documentation generation.
     * If not provided, the feature name will be auto-generated from the method name.
     *
     * @return the descriptive name for documentation, or empty string if not set
     */
    String docDescription() default "";
}
