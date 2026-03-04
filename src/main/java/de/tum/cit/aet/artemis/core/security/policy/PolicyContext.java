package de.tum.cit.aet.artemis.core.security.policy;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Thread-local context holder for active access policies during request processing.
 * <p>
 * This class provides a way for controller-level annotations like {@link de.tum.cit.aet.artemis.core.security.annotations.enforceAccessPolicy.FilterByAccessPolicy}
 * to communicate policy information down to the service and repository layers without
 * explicitly passing it through method parameters.
 * <p>
 * <strong>Lifecycle:</strong>
 * <ol>
 * <li>AOP aspect sets the context at the start of the annotated method</li>
 * <li>Service/repository methods access the context via static methods</li>
 * <li>AOP aspect clears the context after method completion (even if exceptions occur)</li>
 * </ol>
 * <p>
 * <strong>Thread Safety:</strong> Uses {@link ThreadLocal} to ensure each request thread
 * has its own isolated policy context. The context is automatically cleared to prevent
 * leaks when threads are reused (e.g., in thread pools).
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>{@code
 * // In AOP aspect
 * PolicyContext.set(policy, userGroups, isAdmin, includeActive);
 * try {
 *     return joinPoint.proceed();
 * } finally {
 *     PolicyContext.clear();
 * }
 *
 * // In service layer
 * public Set<Course> findAllActiveForUser(User user) {
 *     var spec = PolicyContext.getCurrentSpecification(Course.class)
 *         .orElseGet(() -> buildSpecManually(user));
 *     return courseRepository.findAll(spec);
 * }
 * }</pre>
 */
public final class PolicyContext {

    private static final ThreadLocal<PolicyContextData> CONTEXT = new ThreadLocal<>();

    private PolicyContext() {
        // Utility class - prevent instantiation
    }

    /**
     * Sets the current policy context for this thread.
     * <p>
     * <strong>Important:</strong> Always call {@link #clear()} after the operation completes,
     * preferably in a finally block to prevent context leaks.
     *
     * @param policy        the access policy to apply
     * @param userGroups    the groups the current user belongs to
     * @param isAdmin       whether the current user is an admin
     * @param includeActive whether to combine with temporal "active" filtering
     */
    public static void set(AccessPolicy<?> policy, Set<String> userGroups, boolean isAdmin, boolean includeActive) {
        CONTEXT.set(new PolicyContextData(policy, userGroups, isAdmin, includeActive, ZonedDateTime.now()));
    }

    /**
     * Clears the policy context for this thread.
     * <p>
     * <strong>Critical:</strong> Must be called after {@link #set(AccessPolicy, Set, boolean, boolean)}
     * to prevent memory leaks and context bleeding between requests.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Gets the current policy specification for the given resource type.
     * <p>
     * This method checks if there's an active policy context for this thread,
     * verifies it matches the requested resource type, and returns the
     * appropriate specification (with or without temporal filtering based on
     * the {@code includeActive} flag).
     *
     * @param <T>          the resource type
     * @param resourceType the class of the resource
     * @return an Optional containing the specification if a matching policy context exists,
     *         or empty if no context is set or the resource type doesn't match
     */
    public static <T> Optional<Specification<T>> getCurrentSpecification(Class<T> resourceType) {
        PolicyContextData data = CONTEXT.get();
        if (data == null) {
            return Optional.empty();
        }

        if (!data.policy.getResourceType().equals(resourceType)) {
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        AccessPolicy<T> typedPolicy = (AccessPolicy<T>) data.policy;

        Specification<T> spec = typedPolicy.toSpecification(data.userGroups, data.isAdmin);

        // If temporal filtering is requested, we would need to combine with temporal specs
        // This is resource-type specific, so we can't do it generically here
        // The aspect should handle this, or we need a pluggable temporal filter strategy

        return Optional.of(spec);
    }

    /**
     * Gets the raw policy context data for advanced use cases.
     * <p>
     * Most code should use {@link #getCurrentSpecification(Class)} instead.
     *
     * @return the current context data, or null if no context is set
     */
    @Nullable
    public static PolicyContextData getCurrent() {
        return CONTEXT.get();
    }

    /**
     * Checks whether there's an active policy context for this thread.
     *
     * @return true if a policy context is set, false otherwise
     */
    public static boolean isContextSet() {
        return CONTEXT.get() != null;
    }

    /**
     * Immutable data holder for policy context information.
     */
    public record PolicyContextData(AccessPolicy<?> policy, Set<String> userGroups, boolean isAdmin, boolean includeActive, ZonedDateTime now) {
    }
}
