package de.tum.cit.aet.artemis.core.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Course_;
import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.Organization_;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.User_;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/**
 * JPA Specifications for Organization-related queries.
 * Contains filter and ordering specs for {@link Organization}, {@link User} (member queries), and {@link Course} (course queries).
 */
public class OrganizationSpecs {

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private static <T> Specification<T> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static String likePattern(@Nullable String term) {
        if (term == null || term.isBlank()) {
            return "%";
        }
        String escaped = term.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    // --------------------------------------------------
    // Organization filter and ordering specs (Specification<Organization>)
    // --------------------------------------------------

    /**
     * Searches organizations whose name, short name, or email pattern contains the given term.
     *
     * @param searchTerm the search term (null or blank matches all)
     * @return specification filtering organizations by the search term
     */
    @NonNull
    public static Specification<Organization> searchOrganizations(@Nullable String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return noOp();
        }
        String pattern = likePattern(searchTerm);
        return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get(Organization_.NAME)), pattern, '\\'), cb.like(cb.lower(root.get(Organization_.SHORT_NAME)), pattern, '\\'),
                cb.like(cb.lower(root.get(Organization_.EMAIL_PATTERN)), pattern, '\\'));
    }

    /**
     * Applies sorting for the organization list view as a {@code CriteriaQuery.orderBy()} side effect.
     * When {@code withCounts} is {@code true} and the sort column is {@code numberOfUsers} or {@code numberOfCourses},
     * correlated subqueries are used to order by the aggregated counts.
     *
     * @param sortedColumn the column to sort by
     * @param sortOrder    ascending or descending
     * @param withCounts   whether counts were requested (affects sort for count columns)
     * @return specification that applies ordering as a side effect and returns {@code null} as predicate
     */
    @NonNull
    public static Specification<Organization> orderedForOrganizations(@Nullable String sortedColumn, SortingOrder sortOrder, boolean withCounts) {
        return (root, query, cb) -> {
            if (query == null || sortedColumn == null || sortedColumn.isBlank()) {
                return null;
            }
            List<Order> orders = new ArrayList<>();
            boolean asc = sortOrder == SortingOrder.ASCENDING;

            Expression<?> sortExpr = switch (sortedColumn) {
                case "name" -> root.get(Organization_.NAME);
                case "shortName" -> root.get(Organization_.SHORT_NAME);
                case "emailPattern" -> root.get(Organization_.EMAIL_PATTERN);
                case "numberOfUsers" -> {
                    if (withCounts) {
                        Subquery<Long> sub = query.subquery(Long.class);
                        Root<Organization> subRoot = sub.from(Organization.class);
                        Join<Organization, User> userJoin = subRoot.join(Organization_.USERS, JoinType.LEFT);
                        sub.select(cb.count(userJoin.get(User_.ID)));
                        sub.where(cb.equal(subRoot.get(Organization_.ID), root.get(Organization_.ID)));
                        yield sub;
                    }
                    yield root.get(DomainObject_.ID);
                }
                case "numberOfCourses" -> {
                    if (withCounts) {
                        Subquery<Long> sub = query.subquery(Long.class);
                        Root<Organization> subRoot = sub.from(Organization.class);
                        Join<Organization, Course> courseJoin = subRoot.join(Organization_.COURSES, JoinType.LEFT);
                        sub.select(cb.count(courseJoin.get(Course_.ID)));
                        sub.where(cb.equal(subRoot.get(Organization_.ID), root.get(Organization_.ID)));
                        yield sub;
                    }
                    yield root.get(DomainObject_.ID);
                }
                default -> root.get(DomainObject_.ID);
            };

            orders.add(asc ? cb.asc(sortExpr) : cb.desc(sortExpr));
            orders.add(cb.asc(root.get(DomainObject_.ID)));
            query.orderBy(orders);
            return null;
        };
    }

    // --------------------------------------------------
    // Member (User) specs (Specification<User>)
    // --------------------------------------------------

    /**
     * Matches users who are members of the given organization.
     *
     * @param organizationId the organization id
     * @return specification filtering users to those belonging to the given organization
     */
    @NonNull
    public static Specification<User> membersInOrganization(long organizationId) {
        return (root, query, cb) -> {
            Join<User, Organization> orgJoin = root.join(User_.ORGANIZATIONS, JoinType.INNER);
            return cb.equal(orgJoin.get(Organization_.ID), organizationId);
        };
    }

    /**
     * Searches members whose login, first name, last name, or email contains the given term.
     *
     * @param searchTerm the search term (null or blank matches all)
     * @return specification filtering members by the search term
     */
    @NonNull
    public static Specification<User> searchMembers(@Nullable String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return noOp();
        }
        String pattern = likePattern(searchTerm);
        return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get(User_.LOGIN)), pattern, '\\'), cb.like(cb.lower(root.get(User_.FIRST_NAME)), pattern, '\\'),
                cb.like(cb.lower(root.get(User_.LAST_NAME)), pattern, '\\'), cb.like(cb.lower(root.get(User_.EMAIL)), pattern, '\\'));
    }

    /**
     * Applies sorting for the member list view as a {@code CriteriaQuery.orderBy()} side effect.
     *
     * @param sortedColumn the column to sort by (login, name, email)
     * @param sortOrder    ascending or descending
     * @return specification that applies ordering as a side effect and returns {@code null} as predicate
     */
    @NonNull
    public static Specification<User> orderedForMembers(@Nullable String sortedColumn, SortingOrder sortOrder) {
        return (root, query, cb) -> {
            if (query == null || sortedColumn == null || sortedColumn.isBlank()) {
                return null;
            }
            List<Order> orders = new ArrayList<>();
            boolean asc = sortOrder == SortingOrder.ASCENDING;

            Expression<String> nameExpr = cb.concat(cb.concat(cb.coalesce(root.get(User_.FIRST_NAME), ""), " "), cb.coalesce(root.get(User_.LAST_NAME), ""));

            Expression<?> sortExpr = switch (sortedColumn) {
                case "login" -> root.get(User_.LOGIN);
                case "name" -> nameExpr;
                case "email" -> root.get(User_.EMAIL);
                default -> root.get(DomainObject_.ID);
            };

            orders.add(asc ? cb.asc(sortExpr) : cb.desc(sortExpr));
            orders.add(cb.asc(root.get(DomainObject_.ID)));
            query.orderBy(orders);
            return null;
        };
    }

    // --------------------------------------------------
    // Course specs (Specification<Course>)
    // --------------------------------------------------

    /**
     * Matches courses that belong to the given organization.
     *
     * @param organizationId the organization id
     * @return specification filtering courses to those linked to the given organization
     */
    @NonNull
    public static Specification<Course> coursesInOrganization(long organizationId) {
        return (root, query, cb) -> {
            Join<Course, Organization> orgJoin = root.join(Course_.ORGANIZATIONS, JoinType.INNER);
            return cb.equal(orgJoin.get(Organization_.ID), organizationId);
        };
    }

    /**
     * Searches courses whose title or short name contains the given term.
     *
     * @param searchTerm the search term (null or blank matches all)
     * @return specification filtering courses by the search term
     */
    @NonNull
    public static Specification<Course> searchCourses(@Nullable String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return noOp();
        }
        String pattern = likePattern(searchTerm);
        return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get(Course_.TITLE)), pattern, '\\'), cb.like(cb.lower(root.get(Course_.SHORT_NAME)), pattern, '\\'));
    }

    /**
     * Applies sorting for the course list view as a {@code CriteriaQuery.orderBy()} side effect.
     *
     * @param sortedColumn the column to sort by (title, shortName)
     * @param sortOrder    ascending or descending
     * @return specification that applies ordering as a side effect and returns {@code null} as predicate
     */
    @NonNull
    public static Specification<Course> orderedForCourses(@Nullable String sortedColumn, SortingOrder sortOrder) {
        return (root, query, cb) -> {
            if (query == null || sortedColumn == null || sortedColumn.isBlank()) {
                return null;
            }
            List<Order> orders = new ArrayList<>();
            boolean asc = sortOrder == SortingOrder.ASCENDING;

            Expression<?> sortExpr = switch (sortedColumn) {
                case "title" -> root.get(Course_.TITLE);
                case "shortName" -> root.get(Course_.SHORT_NAME);
                default -> root.get(DomainObject_.ID);
            };

            orders.add(asc ? cb.asc(sortExpr) : cb.desc(sortExpr));
            orders.add(cb.asc(root.get(DomainObject_.ID)));
            query.orderBy(orders);
            return null;
        };
    }
}
