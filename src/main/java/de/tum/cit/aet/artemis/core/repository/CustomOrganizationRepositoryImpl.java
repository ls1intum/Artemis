package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.repository.OrganizationSpecs.getSearchTermSpecification;

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
import de.tum.cit.aet.artemis.core.dto.OrganizationDTO;
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
            specification = getSearchTermSpecification(searchTerm);
        }

        return findOrganizationsPage(specification, pageable, sortedColumn, sortOrder, withCounts);
    }

    /**
     * Executes the paginated organization query.
     * When {@code withCounts} is {@code true}, LEFT JOINs on users and courses are added and counts
     * are aggregated via GROUP BY. When {@code false}, those joins are omitted for a simpler and faster query.
     */
    private Page<OrganizationDTO> findOrganizationsPage(Specification<Organization> specification, Pageable pageable, String sortedColumn, SortingOrder sortOrder,
            boolean withCounts) {

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<OrganizationDTO> query = builder.createQuery(OrganizationDTO.class);
        Root<Organization> root = query.from(Organization.class);

        Expression<Long> userCountExpr;
        Expression<Long> courseCountExpr;

        if (withCounts) {
            Join<Organization, User> userJoin = root.join(Organization_.USERS, JoinType.LEFT);
            Join<Organization, Course> courseJoin = root.join(Organization_.COURSES, JoinType.LEFT);
            userCountExpr = builder.countDistinct(userJoin.get(User_.ID));
            courseCountExpr = builder.countDistinct(courseJoin.get(Course_.ID));
            // Group by exactly the non-aggregated selected fields (portable JPQL/Criteria behavior)
            query.groupBy(root.get(Organization_.ID), root.get(Organization_.NAME), root.get(Organization_.SHORT_NAME), root.get(Organization_.EMAIL_PATTERN),
                    root.get(Organization_.LOGO_URL));
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
        Expression<?> sortExpr = switch (sortedColumn) {
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

    /**
     * Count total organizations matching the specification
     */
    private long countWithSpecification(CriteriaBuilder builder, Specification<Organization> specification) {
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Organization> countRoot = countQuery.from(Organization.class);

        countQuery.select(builder.countDistinct(countRoot.get(Organization_.ID)));

        if (specification != null) {
            Predicate predicate = specification.toPredicate(countRoot, countQuery, builder);
            if (predicate != null) {
                countQuery.where(predicate);
            }
        }

        return entityManager.createQuery(countQuery).getSingleResult();
    }
}
