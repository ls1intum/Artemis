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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.domain.User_;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.Organization_;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.Course_;

/**
 * Specifications for filtering and querying Organization entities.
 * <p>
 * The Organization entity intentionally has no inverse-side collections to its users or courses
 * (the owning sides on User and Course are sufficient). Member and course lookups therefore start
 * from {@link User} / {@link Course} and join {@code organizations} from the owning side.
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
     * Builds the search predicate for a user root: matches login, first name, last name, or email.
     *
     * @param builder    the criteria builder
     * @param userRoot   the User root or join
     * @param searchTerm the (non-blank) search term
     * @return the OR predicate over user fields
     */
    @NonNull
    public static Predicate getMemberSearchPredicate(CriteriaBuilder builder, From<?, User> userRoot, String searchTerm) {
        return buildSearchPredicate(builder, userRoot, searchTerm, User_.LOGIN, User_.FIRST_NAME, User_.LAST_NAME, User_.EMAIL);
    }

    /**
     * Builds the full WHERE predicate for a member query: filters by organization id (via the owning-side
     * {@link User#getOrganizations()} join) and optionally by search term.
     *
     * @param builder        the criteria builder
     * @param userRoot       the User root (used both for joining organizations and for the search predicate)
     * @param orgJoin        the join from User to Organization (already created by the caller)
     * @param organizationId the id of the organization to filter by
     * @param searchTerm     the search term (may be blank, in which case only the org-id filter is applied)
     * @return the combined predicate
     */
    @NonNull
    public static Predicate getMemberPredicate(CriteriaBuilder builder, Root<User> userRoot, Join<User, Organization> orgJoin, long organizationId, String searchTerm) {
        Predicate orgFilter = builder.equal(orgJoin.get(Organization_.ID), organizationId);
        if (searchTerm == null || searchTerm.isBlank()) {
            return orgFilter;
        }
        return builder.and(orgFilter, getMemberSearchPredicate(builder, userRoot, searchTerm));
    }

    /**
     * Creates a specification over {@link User} that filters by membership in the given organization
     * and optionally by a search term. Designed for use in count queries: the spec adds the join to
     * organizations internally.
     *
     * @param organizationId the id of the organization
     * @param searchTerm     the search term (may be blank, in which case only the org-id filter is applied)
     * @return specification over User with the required join and predicates
     */
    @NonNull
    public static Specification<User> getMemberSpecification(long organizationId, String searchTerm) {
        return (root, query, builder) -> {
            Join<User, Organization> orgJoin = root.join(User_.ORGANIZATIONS, JoinType.INNER);
            return getMemberPredicate(builder, root, orgJoin, organizationId, searchTerm);
        };
    }

    /**
     * Builds the search predicate for a course root: matches title or short name.
     *
     * @param builder    the criteria builder
     * @param courseRoot the Course root or join
     * @param searchTerm the (non-blank) search term
     * @return the OR predicate over course fields
     */
    @NonNull
    public static Predicate getCourseSearchPredicate(CriteriaBuilder builder, From<?, Course> courseRoot, String searchTerm) {
        return buildSearchPredicate(builder, courseRoot, searchTerm, Course_.TITLE, Course_.SHORT_NAME);
    }

    /**
     * Builds the full WHERE predicate for a course query: filters by organization id (via the owning-side
     * {@code Course.getOrganizations()} join) and optionally by search term.
     *
     * @param builder        the criteria builder
     * @param courseRoot     the Course root (used both for joining organizations and for the search predicate)
     * @param orgJoin        the join from Course to Organization (already created by the caller)
     * @param organizationId the id of the organization to filter by
     * @param searchTerm     the search term (may be blank, in which case only the organizationId filter is applied)
     * @return the combined predicate
     */
    @NonNull
    public static Predicate getCoursePredicate(CriteriaBuilder builder, Root<Course> courseRoot, Join<Course, Organization> orgJoin, long organizationId, String searchTerm) {
        Predicate orgFilter = builder.equal(orgJoin.get(Organization_.ID), organizationId);
        if (searchTerm == null || searchTerm.isBlank()) {
            return orgFilter;
        }
        return builder.and(orgFilter, getCourseSearchPredicate(builder, courseRoot, searchTerm));
    }

    /**
     * Creates a specification over {@link Course} that filters by membership in the given organization
     * and optionally by a search term. Designed for use in count queries: the spec adds the join to
     * organizations internally.
     *
     * @param organizationId the id of the organization
     * @param searchTerm     the search term (may be blank, in which case only the organizationId filter is applied)
     * @return specification over Course with the required join and predicates
     */
    @NonNull
    public static Specification<Course> getCourseSpecification(long organizationId, String searchTerm) {
        return (root, query, builder) -> {
            Join<Course, Organization> orgJoin = root.join(Course_.ORGANIZATIONS, JoinType.INNER);
            return getCoursePredicate(builder, root, orgJoin, organizationId, searchTerm);
        };
    }
}
