package de.tum.cit.aet.artemis.core.config.validator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = Base64UrlValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface Base64Url {

    /**
     * Error message to be returned if the validation fails.
     *
     * @return the error message
     */
    String message() default "must be a valid base64url-encoded string";

    /**
     * Groups for categorizing constraints.
     *
     * @return the groups
     */
    Class<?>[] groups() default {};

    /**
     * Payload for clients to specify additional information about the validation failure.
     *
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};
}
