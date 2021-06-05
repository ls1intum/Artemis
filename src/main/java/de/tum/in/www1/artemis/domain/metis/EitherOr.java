package de.tum.in.www1.artemis.domain.metis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = { EitherOrValidator.class })
public @interface EitherOr {

    /**
     * @return message that is shown in case of violation
     */
    String message() default "A post can only be linked to either an exercise context, a lecture context or a course-wide context ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
