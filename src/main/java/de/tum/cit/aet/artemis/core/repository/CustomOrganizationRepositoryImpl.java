package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.repository.OrganizationSpecs.getCoursePredicate;
import static de.tum.cit.aet.artemis.core.repository.OrganizationSpecs.getCourseSpecification;
import static de.tum.cit.aet.artemis.core.repository.OrganizationSpecs.getMemberPredicate;
import static de.tum.cit.aet.artemis.core.repository.OrganizationSpecs.getMemberSpecification;
import static de.tum.cit.aet.artemis.core.repository.OrganizationSpecs.getOrganizationSpecification;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Course_;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.Organization_;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.domain.User_;
import de.tum.cit.aet.artemis.core.dto.OrganizationCourseDTO;
import de.tum.cit.aet.artemis.core.dto.OrganizationDTO;
import de.tum.cit.aet.artemis.core.dto.OrganizationMemberDTO;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;

/**
 * Implementation of organization repository fragment with advanced filtering and aggregation
 */
public class CustomOrganizationRepositoryImpl implements CustomOrganizationRepository {

    private final EntityManager entityManager;

    public CustomOrganizationRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<OrganizationDTO> getAllOrganizations(SearchTermPageableSearchDTO<String> search, boolean withCounts) {
        final String searchTerm = search.getSearchTerm();
        final int page = search.getPage();
        final int pageSize = search.getPageSize();
        final String sortedColumn = search.getSortedColumn();
        final SortingOrder sortOrder = search.getSortingOrder();

        // Pageable is only used for offset/limit; sorting is applied directly in the CriteriaQuery
        // because we also support sorting by aggregated counts.
        final Pageable pageable = PageRequest.of(page, pageSize);

        Specification<Organization> specification = null;
        if (searchTerm != null && !searchTerm.isBlank()) {
            specification = getOrganizationSpecification(searchTerm);
        }

        return findOrganizationsPage(specification, pageable, sortedColumn, sortOrder, withCounts);
    }

    /**
     * Executes the paginated organization query.
     * When {@code withCounts} is {@code true}, correlated subqueries are added to count users and courses
     * per organization. When {@code false}, those subqueries are omitted for a simpler and faster query.
     */
    private Page<OrganizationDTO> findOrganizationsPage(Specification<Organization> specification, Pageable pageable, String sortedColumn, SortingOrder sortOrder,
            boolean withCounts) {

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<OrganizationDTO> query = builder.createQuery(OrganizationDTO.class);
        Root<Organization> root = query.from(Organization.class);

        Expression<Long> userCountExpr;
        Expression<Long> courseCountExpr;

        if (withCounts) {
            // Correlated subqueries avoid the Cartesian product that dual LEFT JOINs would produce
            Subquery<Long> userCountSub = query.subquery(Long.class);
            Root<Organization> ucRoot = userCountSub.from(Organization.class);
            Join<Organization, User> ucJoin = ucRoot.join(Organization_.USERS, JoinType.LEFT);
            userCountSub.select(builder.count(ucJoin.get(User_.ID)));
            userCountSub.where(builder.equal(ucRoot.get(Organization_.ID), root.get(Organization_.ID)));
            userCountExpr = userCountSub;

            Subquery<Long> courseCountSub = query.subquery(Long.class);
            Root<Organization> ccRoot = courseCountSub.from(Organization.class);
            Join<Organization, Course> ccJoin = ccRoot.join(Organization_.COURSES, JoinType.LEFT);
            courseCountSub.select(builder.count(ccJoin.get(Course_.ID)));
            courseCountSub.where(builder.equal(ccRoot.get(Organization_.ID), root.get(Organization_.ID)));
            courseCountExpr = courseCountSub;
        }
        else {
            userCountExpr = builder.nullLiteral(Long.class);
            courseCountExpr = builder.nullLiteral(Long.class);
        }

        query.select(builder.construct(OrganizationDTO.class, root.get(Organization_.ID), root.get(Organization_.NAME), root.get(Organization_.SHORT_NAME),
                root.get(Organization_.EMAIL_PATTERN), root.get(Organization_.LOGO_URL), userCountExpr, courseCountExpr));

        // Filtering
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, query, builder);
            if (predicate != null) {
                query.where(predicate);
            }
        }

        // Sorting — aggregate sort fields fall back to id when counts are not loaded
        final String effectiveSortedColumn = sortedColumn != null ? sortedColumn : "";
        Expression<?> sortExpr = switch (effectiveSortedColumn) {
            case "name" -> root.get(Organization_.NAME);
            case "shortName" -> root.get(Organization_.SHORT_NAME);
            case "emailPattern" -> root.get(Organization_.EMAIL_PATTERN);
            case "numberOfUsers" -> withCounts ? userCountExpr : root.get(Organization_.ID);
            case "numberOfCourses" -> withCounts ? courseCountExpr : root.get(Organization_.ID);
            default -> root.get(Organization_.ID);
        };

        Order primaryOrder = (sortOrder == SortingOrder.DESCENDING) ? builder.desc(sortExpr) : builder.asc(sortExpr);
        // Tie-breaker for stable pagination (especially when many orgs share the same count)
        Order tieBreaker = builder.asc(root.get(Organization_.ID));
        query.orderBy(primaryOrder, tieBreaker);

        TypedQuery<OrganizationDTO> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<OrganizationDTO> results = typedQuery.getResultList();
        long total = countWithSpecification(builder, specification);

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public Page<OrganizationMemberDTO> getUsersByOrganizationId(long organizationId, SearchTermPageableSearchDTO<String> search) {
        final String searchTerm = search.getSearchTerm();
        final int page = search.getPage();
        final int pageSize = search.getPageSize();
        final String sortedColumn = search.getSortedColumn();
        final SortingOrder sortOrder = search.getSortingOrder();

        final Pageable pageable = PageRequest.of(page, pageSize);

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<OrganizationMemberDTO> query = builder.createQuery(OrganizationMemberDTO.class);
        Root<Organization> root = query.from(Organization.class);
        Join<Organization, User> u = root.join(Organization_.USERS, JoinType.INNER);

        Expression<String> nameExpr = builder.concat(builder.concat(builder.coalesce(u.get(User_.FIRST_NAME), ""), " "), builder.coalesce(u.get(User_.LAST_NAME), ""));

        query.select(builder.construct(OrganizationMemberDTO.class, u.get(User_.ID), u.get(User_.LOGIN), nameExpr, u.get(User_.EMAIL)));

        // Filtering
        query.where(getMemberPredicate(builder, root, u, organizationId, searchTerm));

        // Sorting
        final String effectiveSortedColumn = sortedColumn != null ? sortedColumn : "";
        Expression<?> sortExpr = switch (effectiveSortedColumn) {
            case "login" -> u.get(User_.LOGIN);
            case "name" -> nameExpr;
            case "email" -> u.get(User_.EMAIL);
            default -> u.get(User_.ID);
        };

        Order primaryOrder = (sortOrder == SortingOrder.DESCENDING) ? builder.desc(sortExpr) : builder.asc(sortExpr);
        // Tie-breaker for stable pagination
        Order tieBreaker = builder.asc(u.get(User_.ID));
        query.orderBy(primaryOrder, tieBreaker);

        TypedQuery<OrganizationMemberDTO> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<OrganizationMemberDTO> results = typedQuery.getResultList();
        long total = countWithSpecification(builder, getMemberSpecification(organizationId, searchTerm));

        return new PageImpl<>(results, pageable, total);
    }

    @Override
    public Page<OrganizationCourseDTO> getCoursesByOrganizationId(long organizationId, SearchTermPageableSearchDTO<String> search) {
        final String searchTerm = search.getSearchTerm();
        final int page = search.getPage();
        final int pageSize = search.getPageSize();
        final String sortedColumn = search.getSortedColumn();
        final SortingOrder sortOrder = search.getSortingOrder();

        final Pageable pageable = PageRequest.of(page, pageSize);

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<OrganizationCourseDTO> query = builder.createQuery(OrganizationCourseDTO.class);
        Root<Organization> root = query.from(Organization.class);
        Join<Organization, Course> c = root.join(Organization_.COURSES, JoinType.INNER);

        query.select(builder.construct(OrganizationCourseDTO.class, c.get(Course_.ID), c.get(Course_.TITLE), c.get(Course_.SHORT_NAME)));

        // Filtering
        query.where(getCoursePredicate(builder, root, c, organizationId, searchTerm));

        // Sorting
        final String effectiveSortedColumn = sortedColumn != null ? sortedColumn : "";
        Expression<?> sortExpr = switch (effectiveSortedColumn) {
            case "title" -> c.get(Course_.TITLE);
            case "shortName" -> c.get(Course_.SHORT_NAME);
            default -> c.get(Course_.ID);
        };

        Order primaryOrder = (sortOrder == SortingOrder.DESCENDING) ? builder.desc(sortExpr) : builder.asc(sortExpr);
        // Tie-breaker for stable pagination
        Order tieBreaker = builder.asc(c.get(Course_.ID));
        query.orderBy(primaryOrder, tieBreaker);

        TypedQuery<OrganizationCourseDTO> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<OrganizationCourseDTO> results = typedQuery.getResultList();
        long total = countWithSpecification(builder, getCourseSpecification(organizationId, searchTerm));

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * Returns the total number of rows matching the given specification.
     */
    private long countWithSpecification(CriteriaBuilder builder, Specification<Organization> specification) {
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Organization> countRoot = countQuery.from(Organization.class);

        if (specification != null) {
            Predicate predicate = specification.toPredicate(countRoot, countQuery, builder);
            if (predicate != null) {
                countQuery.where(predicate);
            }
        }

        countQuery.select(builder.count(countRoot));
        return entityManager.createQuery(countQuery).getSingleResult();
    }
}
