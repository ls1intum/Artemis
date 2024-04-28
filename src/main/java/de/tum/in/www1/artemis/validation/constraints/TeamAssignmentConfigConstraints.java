package de.tum.in.www1.artemis.validation.constraints;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import de.tum.in.www1.artemis.validation.TeamAssignmentConfigValidator;

/**
 * Custom constraint annotation for team assignment configurations
 * Validation is performed by TeamAssignmentConfigValidator before saving. If it fails, an error will be thrown.
 */
@Constraint(validatedBy = TeamAssignmentConfigValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TeamAssignmentConfigConstraints {

    /**
     * @return the message of the violated constraint
     */
    String message() default "{de.tum.in.www1.artemis.validation.constraints.TeamAssignmentConfigConstraints}";

    /**
     * @return the groups, default is empty
     */
    Class<?>[] groups() default {};

    /**
     * @return the payload, default is empty
     */
    Class<? extends Payload>[] payload() default {};
}
