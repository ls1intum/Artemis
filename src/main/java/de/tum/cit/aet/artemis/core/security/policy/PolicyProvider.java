package de.tum.cit.aet.artemis.core.security.policy;

/**
 * Interface for components that provide access policies.
 * Policy provider classes are Spring-managed beans that supply an {@link AccessPolicy}
 * and can be referenced in {@link de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy.EnforceAccessPolicy} annotations
 * for type-safe, IDE-navigable policy references.
 *
 * <p>
 * Example implementation:
 *
 * <pre>
 *
 * {
 *     &#64;code
 *     &#64;Component
 *     public class CourseVisibilityPolicy implements PolicyProvider<Course> {
 *
 *         private final AccessPolicy<Course> policy = AccessPolicy.forResource(Course.class).named("course-visibility").section("Navigation").feature("Course Overview")
 *                 .rule(when(isStaff()).thenAllow()).rule(when(isStudent().and(hasStarted(Course::getStartDate))).thenAllow()).denyByDefault();
 *
 *         @Override
 *         public AccessPolicy<Course> getPolicy() {
 *             return policy;
 *         }
 *     }
 * }
 * </pre>
 *
 * @param <T> the type of resource the policy protects
 */
public interface PolicyProvider<T> {

    /**
     * Returns the access policy provided by this component.
     *
     * @return the access policy
     */
    AccessPolicy<T> getPolicy();
}
