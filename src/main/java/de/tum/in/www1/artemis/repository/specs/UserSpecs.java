package de.tum.in.www1.artemis.repository.specs;

import java.util.Arrays;
import java.util.Set;

import javax.persistence.criteria.*;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.*;

/**
 * This class contains possible specifications to query for specified users.
 */
public class UserSpecs {

    static long FILTER_EMPTY_COURSES = -1;

    static String FILTER_NO_AUTHORITY = "ROLE_NO_AUTHORITY";

    /**
     * Creates the specification to match the provided search term.
     *
     * @param searchTerm term to match
     * @return specification used to chain database operations
     */
    public static Specification<User> getSearchTermSpecification(String searchTerm) {
        String extendedSearchTerm = "%" + searchTerm + "%";
        return (root, query, criteriaBuilder) -> {
            String[] columns = new String[] { User_.LOGIN, User_.EMAIL, User_.FIRST_NAME, User_.LAST_NAME };
            Predicate[] predicates = Arrays.stream(columns).map((column) -> criteriaBuilder.like(root.get(column), extendedSearchTerm)).toArray(Predicate[]::new);

            return criteriaBuilder.or(predicates);
        };
    }

    /**
     * This method creates the specification that matches the specified authorities.
     * Each user must match all the specified authorities.
     * Users with more than the required authorities are also returned.
     *
     * @param authorities set of possible authorities
     * @return specification used to chain database operations
     */
    public static Specification<User> getAllUsersMatchingAuthorities(Set<String> authorities) {
        return (root, query, criteriaBuilder) -> {
            Join<User, Authority> joinedAuthorities = root.join(User_.AUTHORITIES, JoinType.LEFT);
            joinedAuthorities.on(criteriaBuilder.in(joinedAuthorities.get(Authority_.NAME)).value(authorities));

            query.groupBy(root.get(User_.ID)).having(criteriaBuilder.equal(criteriaBuilder.count(joinedAuthorities), authorities.size()));

            return null;
        };
    }

    /**
     * This method creates the specification that matches the empty authorities. That mean that we match all users without any authority.
     *
     * @return specification used to chain database operations
     */
    public static Specification<User> getAllUsersMatchingEmptyAuthorities() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get(User_.AUTHORITIES));
    }

    /**
     * This method returns the selected authority specification based on the provided list of authorities.
     *
     * @param authorities provided authorities
     * @param courseIds a set of courseIds which the users need to match
     * @return specification used to chain database operations
     */
    public static Specification<User> getAuthoritySpecification(Set<String> authorities, Set<Long> courseIds) {
        if (authorities.contains(FILTER_NO_AUTHORITY)) {
            // Empty authorities
            return getAllUsersMatchingEmptyAuthorities();
        }
        else if (!authorities.isEmpty() && (courseIds.isEmpty() || courseIds.contains(FILTER_EMPTY_COURSES))) {
            // Match all authorities
            return getAllUsersMatchingAuthorities(authorities);
        }
        return null;
    }

    /**
     * Creates the specification to match the state of the user (internal or external).
     *
     * @param internal true if the account should be internal
     * @param external true if the account should be external
     * @return specification used to chain database operations
     */
    public static Specification<User> getInternalOrExternalSpecification(boolean internal, boolean external) {
        if (!internal && !external) {
            return null;
        }
        else {
            return (root, query, criteriaBuilder) -> {
                Predicate internalPredicate = criteriaBuilder.equal(root.get(User_.IS_INTERNAL), internal);
                Predicate externalPredicate = criteriaBuilder.notEqual(root.get(User_.IS_INTERNAL), external);

                return criteriaBuilder.and(internalPredicate, externalPredicate);
            };
        }
    }

    /**
     * Creates the specification to match the state of the user (activated or deactivated).
     *
     * @param activated   true if the account should be activated
     * @param deactivated true if the account should be deactivated
     * @return specification used to chain database operations
     */
    public static Specification<User> getActivatedOrDeactivatedSpecification(boolean activated, boolean deactivated) {
        if (!activated && !deactivated) {
            return null;
        }
        else {
            return (root, query, criteriaBuilder) -> {
                Predicate activatedPredicate = criteriaBuilder.equal(root.get(User_.ACTIVATED), activated);
                Predicate deactivatedPredicate = criteriaBuilder.notEqual(root.get(User_.ACTIVATED), deactivated);

                return criteriaBuilder.and(activatedPredicate, deactivatedPredicate);
            };
        }
    }

    /**
     * Creates the specification to match the users course.
     *
     * @return specification used to chain database operations
     */
    public static Specification<User> getAllUsersMatchingEmptyCourses() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get(User_.GROUPS));
    }

    /**
     * Creates the specification to match the users course.
     *
     * @param courseIds a set of courseIds which the users need to match
     * @return specification used to chain database operations
     */
    public static Specification<User> getAllUsersMatchingCourses(Set<Long> courseIds) {
        return (root, query, criteriaBuilder) -> {
            Root<Course> courseRoot = query.from(Course.class);

            Join<User, String> group = root.join(User_.GROUPS, JoinType.LEFT);

            // Select all possible group types
            String[] columns = new String[] { Course_.STUDENT_GROUP_NAME, Course_.TEACHING_ASSISTANT_GROUP_NAME, Course_.EDITOR_GROUP_NAME, Course_.INSTRUCTOR_GROUP_NAME };
            Predicate[] predicates = Arrays.stream(columns).map((column) -> criteriaBuilder.in(courseRoot.get(column)).value(group)).toArray(Predicate[]::new);

            // The course needs to be one of the selected
            Predicate inCourse = criteriaBuilder.in(courseRoot.get(Course_.ID)).value(courseIds);

            group.on(criteriaBuilder.and(criteriaBuilder.or(predicates), inCourse));

            query.groupBy(root.get(User_.ID)).having(criteriaBuilder.equal(criteriaBuilder.count(group), courseIds.size()));

            return null;
        };
    }

    /**
     * Creates the specification to match the users course.
     *
     * @param courseIds a set of courseIds which the users need to match at least one
     * @return specification used to chain database operations
     */
    public static Specification<User> getCourseSpecification(Set<Long> courseIds) {
        if (courseIds.size() == 1 && courseIds.contains(FILTER_EMPTY_COURSES)) {
            // Empty courses
            return getAllUsersMatchingEmptyCourses();
        }
        else if (!courseIds.isEmpty()) {
            // Match all selected
            return getAllUsersMatchingCourses(courseIds);
        }
        return null;
    }

    /**
     * Creates the specification for authorities and courses.
     * Since for some reason we can't combine both filters with joins, we need to switch to sub query.
     *
     * @param courseIds a set of courseIds which the users need to match at least one
     * @param authorities set of possible authorities
     * @return specification used to chain database operations
     */
    public static Specification<User> getAuthorityAndCourseSpecification(Set<Long> courseIds, Set<String> authorities) {
        return (root, query, criteriaBuilder) -> {
            if ((!courseIds.isEmpty() && !courseIds.contains(FILTER_EMPTY_COURSES)) && (!authorities.isEmpty() && !authorities.contains(FILTER_NO_AUTHORITY))) {
                Subquery<Long> subQuery = query.subquery(Long.class);
                Root<User> subUserRoot = subQuery.from(User.class);
                Join<User, Authority> joinedAuthorities = subUserRoot.join(User_.AUTHORITIES, JoinType.LEFT);

                subQuery.select(subUserRoot.get(User_.ID))
                        .where(criteriaBuilder.and(criteriaBuilder.in(joinedAuthorities.get(Authority_.NAME)).value(authorities),
                                criteriaBuilder.equal(subUserRoot.get(User_.ID), root.get(User_.ID))))
                        .groupBy(subUserRoot.get(User_.ID)).having(criteriaBuilder.equal(criteriaBuilder.count(joinedAuthorities), authorities.size()));

                return criteriaBuilder.and(criteriaBuilder.exists(subQuery));
            }
            return null;
        };
    }

    /**
     * Creates the specification to get distinct results.
     *
     * @return specification used to chain database operations
     */
    public static Specification<User> distinct() {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            return null;
        };
    }
}
