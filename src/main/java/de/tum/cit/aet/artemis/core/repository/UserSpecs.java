package de.tum.cit.aet.artemis.core.repository;

import java.util.Arrays;
import java.util.Set;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Authority_;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.User_;

/**
 * This class contains possible specifications to query for specified users.
 */
public class UserSpecs {

    private static final String FILTER_NO_AUTHORITY = "ROLE_NO_AUTHORITY";

    /**
     * Creates the specification to match the provided search term within the user’s login, email, and name attributes.
     *
     * @param searchTerm term to match
     * @return specification used to chain database operations
     */
    public static Specification<User> getSearchTermSpecification(String searchTerm) {
        String extendedSearchTerm = "%" + searchTerm + "%";
        return (root, query, criteriaBuilder) -> {
            String[] columns = { User_.LOGIN, User_.EMAIL, User_.FIRST_NAME, User_.LAST_NAME };
            Predicate[] predicates = Arrays.stream(columns).map(column -> criteriaBuilder.like(root.get(column), extendedSearchTerm)).toArray(Predicate[]::new);

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
     * @return specification used to chain database operations
     */
    public static Specification<User> getAuthoritySpecification(Set<String> authorities) {
        if (authorities.contains(FILTER_NO_AUTHORITY)) {
            // Empty authorities
            return getAllUsersMatchingEmptyAuthorities();
        }
        else if (!authorities.isEmpty()) {
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
     * Creates the specification to look for the user without Registration Numbers.
     *
     * @param noRegistrationNumber   true if the account should not have a registration number
     * @param withRegistrationNumber true if the account should have a registration number
     * @return specification used to chain database operations
     */
    public static Specification<User> getWithOrWithoutRegistrationNumberSpecification(Boolean noRegistrationNumber, Boolean withRegistrationNumber) {
        if (!noRegistrationNumber && !withRegistrationNumber) {
            return null;
        }
        else {
            return (root, query, criteriaBuilder) -> {
                if (noRegistrationNumber) {
                    Predicate userWithoutRegistrationNumber = criteriaBuilder.isNull(root.get(User_.REGISTRATION_NUMBER));
                    return criteriaBuilder.and(userWithoutRegistrationNumber);
                }
                else {
                    Predicate userWithRegistrationNumber = criteriaBuilder.notEqual(root.get(User_.REGISTRATION_NUMBER), "");
                    return criteriaBuilder.and(userWithRegistrationNumber);
                }
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
     * Creates the specification to find all users that are assigned no user groups.
     *
     * @return specification used to chain database operations
     */
    public static Specification<User> getAllUsersWithoutUserGroups() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isEmpty(root.get(User_.GROUPS));
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

    /**
     * Creates the specification to get results that were not soft-deleted.
     *
     * @return specification used to chain database operations
     */
    public static Specification<User> notSoftDeleted() {
        return (root, query, criteriaBuilder) -> {
            Predicate notDeletedPredicate = criteriaBuilder.equal(root.get(User_.IS_DELETED), false);

            return criteriaBuilder.and(notDeletedPredicate);
        };
    }
}
