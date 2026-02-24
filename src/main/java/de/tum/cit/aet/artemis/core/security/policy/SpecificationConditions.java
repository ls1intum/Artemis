package de.tum.cit.aet.artemis.core.security.policy;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.function.Function;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Enhanced condition vocabulary for the policy DSL that supports both runtime evaluation
 * and automatic conversion to JPA Specifications for database queries.
 * <p>
 * These conditions implement {@link SpecificationCondition}, meaning they can be used both for:
 * <ul>
 * <li>Runtime authorization checks: {@code condition.test(user, resource)}</li>
 * <li>Database query generation: {@code condition.toPredicate(root, cb, userGroups, isAdmin)}</li>
 * </ul>
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * import static de.tum.cit.aet.artemis.core.security.policy.SpecificationConditions.*;
 *
 * // Define policy
 * AccessPolicy<Course> policy = AccessPolicy.forResource(Course.class).rule(when(memberOfGroup(Course::getStudentGroupName, Course_.STUDENT_GROUP_NAME)).thenAllow())
 *         .denyByDefault();
 *
 * // Use for runtime check
 * boolean canAccess = policy.evaluate(user, course) == PolicyEffect.ALLOW;
 *
 * // Use for database query
 * Specification<Course> spec = policy.toSpecification(userGroups, isAdmin);
 *
 * List<Course> courses = courseRepository.findAll(spec);
 * }</pre>
 */
public final class SpecificationConditions {

    private SpecificationConditions() {
        // utility class
    }

    /**
     * Condition that checks whether the user belongs to a group extracted from the resource.
     * <p>
     * Runtime behavior: Checks if {@code user.getGroups().contains(groupExtractor.apply(resource))}
     * <br>
     * Query behavior: Generates {@code WHERE groupAttribute IN :userGroups}
     *
     * @param <T>            the resource type
     * @param groupExtractor function that extracts the group name from the resource (for runtime checks)
     * @param groupAttribute the JPA metamodel attribute for the group field (for query generation)
     * @return a condition that is true when the user is a member of the extracted group
     */
    public static <T> SpecificationCondition<T> memberOfGroup(Function<T, String> groupExtractor, SingularAttribute<T, String> groupAttribute) {
        return new SpecificationCondition<>() {

            @Override
            public boolean test(User user, T resource) {
                String group = groupExtractor.apply(resource);
                return group != null && user.getGroups().contains(group);
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                return root.get(groupAttribute).in(userGroups);
            }
        };
    }

    /**
     * Condition that checks whether a date extracted from the resource is null or in the past (has started).
     * <p>
     * Runtime behavior: Checks if {@code date == null || date.isBefore(now)}
     * <br>
     * Query behavior: Generates {@code WHERE (dateAttribute IS NULL OR dateAttribute <= :now)}
     *
     * @param <T>           the resource type
     * @param dateExtractor function that extracts the date from the resource (for runtime checks)
     * @param dateAttribute the JPA metamodel attribute for the date field (for query generation)
     * @return a condition that is true when the date is null or before now
     */
    public static <T> SpecificationCondition<T> hasStarted(Function<T, ZonedDateTime> dateExtractor, SingularAttribute<T, ZonedDateTime> dateAttribute) {
        return new SpecificationCondition<>() {

            @Override
            public boolean test(User user, T resource) {
                ZonedDateTime date = dateExtractor.apply(resource);
                return date == null || date.isBefore(ZonedDateTime.now()) || date.isEqual(ZonedDateTime.now());
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                ZonedDateTime now = ZonedDateTime.now();
                return criteriaBuilder.or(criteriaBuilder.isNull(root.get(dateAttribute)), criteriaBuilder.lessThanOrEqualTo(root.get(dateAttribute), now));
            }
        };
    }

    /**
     * Condition that checks whether a date extracted from the resource is null or in the future (has not ended).
     * <p>
     * Runtime behavior: Checks if {@code date == null || date.isAfter(now)}
     * <br>
     * Query behavior: Generates {@code WHERE (dateAttribute IS NULL OR dateAttribute >= :now)}
     *
     * @param <T>           the resource type
     * @param dateExtractor function that extracts the date from the resource (for runtime checks)
     * @param dateAttribute the JPA metamodel attribute for the date field (for query generation)
     * @return a condition that is true when the date is null or after now
     */
    public static <T> SpecificationCondition<T> hasNotEnded(Function<T, ZonedDateTime> dateExtractor, SingularAttribute<T, ZonedDateTime> dateAttribute) {
        return new SpecificationCondition<>() {

            @Override
            public boolean test(User user, T resource) {
                ZonedDateTime date = dateExtractor.apply(resource);
                return date == null || date.isAfter(ZonedDateTime.now()) || date.isEqual(ZonedDateTime.now());
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                ZonedDateTime now = ZonedDateTime.now();
                return criteriaBuilder.or(criteriaBuilder.isNull(root.get(dateAttribute)), criteriaBuilder.greaterThanOrEqualTo(root.get(dateAttribute), now));
            }
        };
    }

    /**
     * Condition that checks whether the user has ADMIN or SUPER_ADMIN authority.
     * <p>
     * Runtime behavior: Checks if user has ADMIN or SUPER_ADMIN authority
     * <br>
     * Query behavior: Uses the {@code isAdmin} parameter passed to {@code toPredicate()}
     *
     * @param <T> the resource type
     * @return a condition that is true when the user is an admin
     */
    public static <T> SpecificationCondition<T> isAdmin() {
        return new SpecificationCondition<>() {

            @Override
            public boolean test(User user, T resource) {
                return user.getAuthorities().contains(new Authority(Role.ADMIN.getAuthority())) || user.getAuthorities().contains(new Authority(Role.SUPER_ADMIN.getAuthority()));
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                // If admin, return a predicate that's always true (conjunction)
                // If not admin, return a predicate that's always false (disjunction with no predicates)
                return isAdmin ? criteriaBuilder.conjunction() : criteriaBuilder.disjunction();
            }
        };
    }

    /**
     * Condition that always evaluates to true.
     * <p>
     * Runtime behavior: Always returns true
     * <br>
     * Query behavior: Generates a conjunction (always true predicate)
     *
     * @param <T> the resource type
     * @return a condition that is always true
     */
    public static <T> SpecificationCondition<T> always() {
        return new SpecificationCondition<>() {

            @Override
            public boolean test(User user, T resource) {
                return true;
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                return criteriaBuilder.conjunction();
            }
        };
    }

    /**
     * Condition that always evaluates to false.
     * <p>
     * Runtime behavior: Always returns false
     * <br>
     * Query behavior: Generates a disjunction (always false predicate)
     *
     * @param <T> the resource type
     * @return a condition that is always false
     */
    public static <T> SpecificationCondition<T> never() {
        return new SpecificationCondition<>() {

            @Override
            public boolean test(User user, T resource) {
                return false;
            }

            @Override
            public Predicate toPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Set<String> userGroups, boolean isAdmin) {
                return criteriaBuilder.disjunction();
            }
        };
    }
}
