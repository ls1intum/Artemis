package de.tum.cit.aet.artemis.core.security.policy;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A composable predicate that tests whether a user satisfies a condition
 * relative to a resource of type {@code T}.
 *
 * @param <T> the type of resource being evaluated
 */
@FunctionalInterface
public interface PolicyCondition<T> {

    /**
     * Tests whether the given user satisfies this condition for the given resource.
     *
     * @param user     the user to evaluate
     * @param resource the resource to evaluate against
     * @return true if the condition is satisfied
     */
    boolean test(User user, T resource);

    /**
     * Returns a condition that is the logical AND of this condition and another.
     *
     * @param other the other condition
     * @return the composed condition
     */
    default PolicyCondition<T> and(PolicyCondition<T> other) {
        return (user, resource) -> this.test(user, resource) && other.test(user, resource);
    }

    /**
     * Returns a condition that is the logical OR of this condition and another.
     *
     * @param other the other condition
     * @return the composed condition
     */
    default PolicyCondition<T> or(PolicyCondition<T> other) {
        return (user, resource) -> this.test(user, resource) || other.test(user, resource);
    }

    /**
     * Returns a condition that is the logical negation of this condition.
     *
     * @return the negated condition
     */
    default PolicyCondition<T> negate() {
        return (user, resource) -> !this.test(user, resource);
    }
}
