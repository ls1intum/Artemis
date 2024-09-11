package de.tum.cit.aet.artemis.security.annotations.enforceRoleInExercise;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.security.Role;

/**
 * All classes or methods annotated with this will check (used for controller classes) if the current user has the specified role in the target exercises course.
 * This is done using a custom aspect
 *
 * @see EnforceRoleInExerciseAspect
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface EnforceRoleInExercise {

    /**
     * The role that is required to access the annotated endpoint
     *
     * @return the role that is required to access the annotated endpoint
     */
    Role value();

    /**
     * The name of the field in the method parameters that contains the exercise id.
     * This is used to extract the exercise id from the method parameters
     *
     * @return the name of the field in the method parameters that contains the exercise id
     */
    String resourceIdFieldName() default "exerciseId";
}
