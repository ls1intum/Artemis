package de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces an {@link de.tum.cit.aet.artemis.core.security.policy.AccessPolicy} on a controller method.
 * <p>
 * The aspect loads the resource entity by ID (extracted from a method parameter) and the current user,
 * then evaluates the named policy via the {@link de.tum.cit.aet.artemis.core.security.policy.PolicyEngine}.
 * If the policy denies access, an {@link de.tum.cit.aet.artemis.core.exception.AccessForbiddenException} is thrown.
 * <p>
 * For best performance, combine with {@code @PreAuthorize("hasRole('USER')")} (or similar) to short-circuit
 * unauthenticated requests before the aspect loads entities from the database.
 *
 * <pre>
 * {@code
 * &#64;GetMapping("exercises/{exerciseId}")
 * &#64;PreAuthorize("hasRole('USER')")
 * &#64;EnforceAccessPolicy(value = "programmingExerciseVisibilityPolicy", resourceIdFieldName = "exerciseId")
 * public ResponseEntity<Exercise> getExercise(@PathVariable Long exerciseId) { ... }
 * }
 * </pre>
 *
 * @see EnforceAccessPolicyAspect
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface EnforceAccessPolicy {

    /**
     * The bean name of the {@link de.tum.cit.aet.artemis.core.security.policy.AccessPolicy} to enforce.
     *
     * @return the policy bean name
     */
    String value();

    /**
     * The name of the method parameter that contains the resource ID.
     * This is used to extract the resource ID from the method parameters at runtime.
     *
     * @return the name of the parameter holding the resource ID
     */
    String resourceIdFieldName() default "exerciseId";
}
