package de.tum.cit.aet.artemis.core.security.policy;

import java.util.Set;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * A policy condition that can be converted to a JPA Specification predicate.
 * This allows access policies to be used both for runtime authorization checks
 * and for building database queries with the same logic.
 *
 * @param <T> the type of resource being evaluated
 */
public interface SpecificationCondition<T> extends PolicyCondition<T> {

    /**
     * Converts this condition to a JPA Criteria API predicate that can be used in queries.
     * This enables the same access logic to be used for both runtime checks and database filtering.
     *
     * @param root            the root entity being queried
     * @param criteriaBuilder the criteria builder for constructing predicates
     * @param userGroups      the groups the current user belongs to
     * @param isAdmin         whether the current user is an admin
     * @return a predicate representing this condition in SQL
     */
    Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin);

    /**
     * Returns a condition that is the logical AND of this condition and another.
     * Both runtime and query-time behavior are composed.
     *
     * @param other the other condition
     * @return the composed condition
     */
    @Override
    default SpecificationCondition<T> and(PolicyCondition<T> other) {
        if (!(other instanceof SpecificationCondition<T> otherSpec)) {
            throw new IllegalArgumentException("Cannot combine SpecificationCondition with non-SpecificationCondition");
        }
        return new SpecificationCondition<>() {

            @Override
            public boolean test(de.tum.cit.aet.artemis.core.domain.User user, T resource) {
                return SpecificationCondition.this.test(user, resource) && other.test(user, resource);
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                Predicate left = SpecificationCondition.this.toPredicate(root, criteriaBuilder, userGroups, isAdmin);
                Predicate right = otherSpec.toPredicate(root, criteriaBuilder, userGroups, isAdmin);
                return criteriaBuilder.and(left, right);
            }
        };
    }

    /**
     * Returns a condition that is the logical OR of this condition and another.
     * Both runtime and query-time behavior are composed.
     *
     * @param other the other condition
     * @return the composed condition
     */
    @Override
    default SpecificationCondition<T> or(PolicyCondition<T> other) {
        if (!(other instanceof SpecificationCondition<T> otherSpec)) {
            throw new IllegalArgumentException("Cannot combine SpecificationCondition with non-SpecificationCondition");
        }
        return new SpecificationCondition<>() {

            @Override
            public boolean test(de.tum.cit.aet.artemis.core.domain.User user, T resource) {
                return SpecificationCondition.this.test(user, resource) || other.test(user, resource);
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                Predicate left = SpecificationCondition.this.toPredicate(root, criteriaBuilder, userGroups, isAdmin);
                Predicate right = otherSpec.toPredicate(root, criteriaBuilder, userGroups, isAdmin);
                return criteriaBuilder.or(left, right);
            }
        };
    }

    /**
     * Returns a condition that is the logical negation of this condition.
     * Both runtime and query-time behavior are negated.
     *
     * @return the negated condition
     */
    @Override
    default SpecificationCondition<T> negate() {
        return new SpecificationCondition<>() {

            @Override
            public boolean test(de.tum.cit.aet.artemis.core.domain.User user, T resource) {
                return !SpecificationCondition.this.test(user, resource);
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                return criteriaBuilder.not(SpecificationCondition.this.toPredicate(root, criteriaBuilder, userGroups, isAdmin));
            }
        };
    }
}
