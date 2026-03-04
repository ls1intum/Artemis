package de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import de.tum.cit.aet.artemis.core.security.policy.PolicyProvider;

/**
 * Annotation to automatically filter collection-returning repository queries
 * using access policy specifications. This enables applying policy-based access
 * control at the database query level instead of in-memory filtering.
 * <p>
 * <strong>How it works:</strong>
 * <ol>
 * <li>Annotate a controller method that returns a collection of resources</li>
 * <li>The aspect intercepts the method call before execution</li>
 * <li>It sets up a ThreadLocal policy context with the user's groups and admin status</li>
 * <li>Service/repository methods can access this context to build policy-based specifications</li>
 * <li>After the method completes, the context is cleaned up</li>
 * </ol>
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>
 * {@code
 *
 * // Controller method
 * &#64;GetMapping("courses/for-dropdown")
 * &#64;EnforceAtLeastStudent
 * @FilterByAccessPolicy(CourseVisibilityPolicy.class)
 * public ResponseEntity<Set<CourseDropdownDTO>> getCoursesForDropdown() {
 *     User user = userRepository.getUserWithGroupsAndAuthorities();
 *     // The service method automatically gets policy-filtered courses
 *     Set<Course> courses = courseService.findAllActiveForUser(user);
 *     return ResponseEntity.ok(mapToDtos(courses));
 * }
 *
 * // Service method - automatically uses policy from context
 * public Set<Course> findAllActiveForUser(User user) {
 *     // PolicyContext provides the active policy specification
 *     var spec = PolicyContext.getCurrentSpecification(Course.class)
 *             .orElseGet(() -> policyBasedCourseSpecs.withVisibilityAccessAndActive(user.getGroups(), authCheckService.isAdmin(user), ZonedDateTime.now()));
 *     return Set.copyOf(courseRepository.findAll(spec));
 * }
 * }
 * </pre>
 * <p>
 * <strong>Comparison with {@link EnforceAccessPolicy}:</strong>
 * <ul>
 * <li>{@link EnforceAccessPolicy}: Single-entity authorization check after loading (runtime check)</li>
 * <li>{@link FilterByAccessPolicy}: Collection filtering at query level (SQL generation)</li>
 * </ul>
 *
 * @see EnforceAccessPolicy
 * @see de.tum.cit.aet.artemis.core.security.policy.AccessPolicy#toSpecification
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterByAccessPolicy {

    /**
     * The policy provider class that supplies the access policy to use for filtering.
     * This class must be a Spring bean implementing {@link PolicyProvider}.
     *
     * @return the policy provider class
     */
    Class<? extends PolicyProvider<?>> value();

    /**
     * Whether to combine the policy specification with temporal "active" filtering.
     * If true, automatically adds conditions for:
     * <ul>
     * <li>startDate is null OR startDate &lt;= now</li>
     * <li>endDate is null OR endDate &gt;= now</li>
     * </ul>
     *
     * @return true to include active filtering, false otherwise (default: false)
     */
    boolean includeActive() default false;
}
