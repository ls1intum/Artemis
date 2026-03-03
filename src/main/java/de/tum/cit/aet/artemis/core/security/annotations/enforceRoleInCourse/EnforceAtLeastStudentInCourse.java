package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.security.access.prepost.PreAuthorize;

import de.tum.cit.aet.artemis.core.security.Role;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('USER')")
@EnforceRoleInCourse(Role.STUDENT)
public @interface EnforceAtLeastStudentInCourse {

    /**
     * The name of the field in the method parameters that contains the course id.
     * This is used to extract the course id from the method parameters
     *
     * @return the name of the field in the method parameters that contains the course id
     */
    @AliasFor(annotation = EnforceRoleInCourse.class)
    String resourceIdFieldName() default "courseId";

    /**
     * Optional descriptive name for documentation generation.
     * If not provided, the feature name will be auto-generated from the method name.
     *
     * @return the descriptive name for documentation, or empty string if not set
     */
    String docDescription() default "";
}
