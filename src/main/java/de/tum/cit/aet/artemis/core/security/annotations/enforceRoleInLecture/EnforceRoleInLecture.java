package de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * All classes or methods annotated with this will check (used for controller classes) if the current user has the specified role in the target lectures course.
 * This is done using a custom aspect
 *
 * @see EnforceRoleInLectureAspect
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface EnforceRoleInLecture {

    /**
     * The role that is required to access the annotated endpoint
     *
     * @return the role that is required to access the annotated endpoint
     */
    Role value();

    /**
     * The name of the field in the method parameters that contains the lecture id.
     * This is used to extract the lecture id from the method parameters
     *
     * @return the name of the field in the method parameters that contains the lecture id
     */
    String resourceIdFieldName() default "lectureId";
}
