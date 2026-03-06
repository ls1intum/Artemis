package de.tum.cit.aet.artemis.core.repository;

import java.util.Arrays;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Course_;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.Organization_;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.User_;

/**
 * Specifications for filtering and querying Organization entities
 */
public class OrganizationSpecs {

    /**
     * Builds a case-insensitive OR LIKE predicate across the given columns of an entity path.
     *
     * @param builder    the criteria builder
     * @param from       the entity path (Root or Join) whose columns are searched
     * @param searchTerm the (non-blank) search term
     * @param columns    the column names to search across
     * @return an OR predicate over all specified columns
     */
    private static Predicate buildSearchPredicate(CriteriaBuilder builder, From<?, ?> from, String searchTerm, String... columns) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return builder.conjunction();
        }
        String escaped = searchTerm.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        Predicate[] predicates = Arrays.stream(columns).map(column -> builder.like(builder.lower(from.get(column)), pattern, '\\')).toArray(Predicate[]::new);
        return builder.or(predicates);
    }

    /**
     * Creates the specification to match the provided search term within the organization's name, short name, and email pattern attributes.
     *
     * @param searchTerm term to match
     * @return specification used to chain database operations
     */
    @NonNull
    public static Specification<Organization> getOrganizationSpecification(String searchTerm) {
        return (root, query, builder) -> buildSearchPredicate(builder, root, searchTerm, Organization_.NAME, Organization_.SHORT_NAME, Organization_.EMAIL_PATTERN);
    }

    /**
     * Builds the search predicate for a user join: matches login, first name, last name, or email.
     * Call this from the data query where the join is already created for the SELECT projection.
     *
     * @param builder    the criteria builder
     * @param u          the join from Organization to User
     * @param searchTerm the (non-blank) search term
     * @return the OR predicate over user fields
     */
    @NonNull
    public static Predicate getMemberSearchPredicate(CriteriaBuilder builder, Join<Organization, User> u, String searchTerm) {
        return buildSearchPredicate(builder, u, searchTerm, User_.LOGIN, User_.FIRST_NAME, User_.LAST_NAME, User_.EMAIL);
    }

    /**
     * Builds the full WHERE predicate for a member query: filters by organization id and optionally by search term.
     * Accepts the existing join so it can be reused without creating a duplicate join.
     *
     * @param builder        the criteria builder
     * @param root           the Organization root
     * @param u              the join from Organization to User (already created by the caller)
     * @param organizationId the id of the organization to filter by
     * @param searchTerm     the search term (may be blank, in which case only the org-id filter is applied)
     * @return the combined predicate
     */
    @NonNull
    public static Predicate getMemberPredicate(CriteriaBuilder builder, Root<Organization> root, Join<Organization, User> u, long organizationId, String searchTerm) {
        Predicate orgFilter = builder.equal(root.get(Organization_.ID), organizationId);
        if (searchTerm == null || searchTerm.isBlank()) {
            return orgFilter;
        }
        return builder.and(orgFilter, getMemberSearchPredicate(builder, u, searchTerm));
    }

    /**
     * Creates a specification that filters organizations by id and optionally filters their members by search term.
     * Designed for use in count queries: the spec adds the join to users internally.
     *
     * @param organizationId the id of the organization
     * @param searchTerm     the search term (may be blank, in which case only the org-id filter is applied)
     * @return specification over Organization with the required join and predicates
     */
    @NonNull
    public static Specification<Organization> getMemberSpecification(long organizationId, String searchTerm) {
        return (root, query, builder) -> {
            Join<Organization, User> u = root.join(Organization_.USERS, JoinType.INNER);
            return getMemberPredicate(builder, root, u, organizationId, searchTerm);
        };
    }

    /**
     * Builds the search predicate for a course join: matches title or short name.
     * Call this from the data query where the join is already created for the SELECT projection.
     *
     * @param builder    the criteria builder
     * @param c          the join from Organization to Course
     * @param searchTerm the (non-blank) search term
     * @return the OR predicate over course fields
     */
    @NonNull
    public static Predicate getCourseSearchPredicate(CriteriaBuilder builder, Join<Organization, Course> c, String searchTerm) {
        return buildSearchPredicate(builder, c, searchTerm, Course_.TITLE, Course_.SHORT_NAME);
    }

    /**
     * Builds the full WHERE predicate for a course query: filters by organization id and optionally by search term.
     * Accepts the existing join so it can be reused without creating a duplicate join.
     *
     * @param builder        the criteria builder
     * @param root           the Organization root
     * @param c              the join from Organization to Course (already created by the caller)
     * @param organizationId the id of the organization to filter by
     * @param searchTerm     the search term (may be blank, in which case only the organizationId filter is applied)
     * @return the combined predicate
     */
    @NonNull
    public static Predicate getCoursePredicate(CriteriaBuilder builder, Root<Organization> root, Join<Organization, Course> c, long organizationId, String searchTerm) {
        Predicate orgFilter = builder.equal(root.get(Organization_.ID), organizationId);
        if (searchTerm == null || searchTerm.isBlank()) {
            return orgFilter;
        }
        return builder.and(orgFilter, getCourseSearchPredicate(builder, c, searchTerm));
    }

    /**
     * Creates a specification that filters organizations by id and optionally filters their courses by search term.
     * Designed for use in count queries: the spec adds the join to courses internally.
     *
     * @param organizationId the id of the organization
     * @param searchTerm     the search term (may be blank, in which case only the organizationId filter is applied)
     * @return specification over Organization with the required join and predicates
     */
    @NonNull
    public static Specification<Organization> getCourseSpecification(long organizationId, String searchTerm) {
        return (root, query, builder) -> {
            Join<Organization, Course> c = root.join(Organization_.COURSES, JoinType.INNER);
            return getCoursePredicate(builder, root, c, organizationId, searchTerm);
        };
    }
}
