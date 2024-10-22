package de.tum.cit.aet.artemis.communication.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Custom constraint annotation for Posts.
 * It defines a validation constraint that Hibernate Validator will check
 * before Hibernate ORM persists or updates the entities using this annotation.
 */
@Constraint(validatedBy = { PostContextConstraintValidator.class })
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface PostConstraints {

    /**
     * @return the message of the violated constraint
     */
    String message() default "{de.tum.cit.aet.artemis.communication.domain.PostConstraints}";

    /**
     * @return the groups, default is empty
     */
    Class<?>[] groups() default {};

    /**
     * @return the payload, default is empty
     */
    Class<? extends Payload>[] payload() default {};
}
