package de.tum.cit.aet.artemis.core.security.policy;

import java.time.ZonedDateTime;
import java.util.function.Function;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Built-in condition vocabulary for the policy DSL.
 * These static methods are designed to be used with static imports.
 *
 * <p>
 * Example:
 *
 * <pre>{@code
 * import static de.tum.cit.aet.artemis.core.security.policy.Conditions.*;
 *
 * when(memberOfGroup(Course::getStudentGroupName).and(hasStarted(Course::getStartDate)))
 *     .thenAllow()
 * }</pre>
 */
public final class Conditions {

    private Conditions() {
        // utility class
    }

    /**
     * Condition that checks whether the user belongs to a group extracted from the resource.
     *
     * @param <T>            the resource type
     * @param groupExtractor function that extracts the group name from the resource
     * @return a condition that is true when the user is a member of the extracted group
     */
    public static <T> PolicyCondition<T> memberOfGroup(Function<T, String> groupExtractor) {
        return (user, resource) -> {
            String group = groupExtractor.apply(resource);
            return group != null && user.getGroups().contains(group);
        };
    }

    /**
     * Condition that checks whether a date extracted from the resource is null or in the past.
     *
     * @param <T>           the resource type
     * @param dateExtractor function that extracts the date from the resource
     * @return a condition that is true when the date is null or before now
     */
    public static <T> PolicyCondition<T> hasStarted(Function<T, ZonedDateTime> dateExtractor) {
        return (user, resource) -> {
            ZonedDateTime date = dateExtractor.apply(resource);
            return date == null || date.isBefore(ZonedDateTime.now());
        };
    }

    /**
     * Condition that checks whether a date extracted from the resource is null or in the future.
     *
     * @param <T>           the resource type
     * @param dateExtractor function that extracts the date from the resource
     * @return a condition that is true when the date is null or after now
     */
    public static <T> PolicyCondition<T> hasNotEnded(Function<T, ZonedDateTime> dateExtractor) {
        return (user, resource) -> {
            ZonedDateTime date = dateExtractor.apply(resource);
            return date == null || date.isAfter(ZonedDateTime.now());
        };
    }

    /**
     * Condition that checks whether the user has ADMIN or SUPER_ADMIN authority.
     *
     * @param <T> the resource type
     * @return a condition that is true when the user is an admin
     */
    public static <T> PolicyCondition<T> isAdmin() {
        return (user, resource) -> user.getAuthorities().contains(new Authority(Role.ADMIN.getAuthority()))
                || user.getAuthorities().contains(new Authority(Role.SUPER_ADMIN.getAuthority()));
    }

    /**
     * Condition that always evaluates to true.
     *
     * @param <T> the resource type
     * @return a condition that is always true
     */
    public static <T> PolicyCondition<T> always() {
        return (user, resource) -> true;
    }

    /**
     * Condition that always evaluates to false.
     *
     * @param <T> the resource type
     * @return a condition that is always false
     */
    public static <T> PolicyCondition<T> never() {
        return (user, resource) -> false;
    }
}
