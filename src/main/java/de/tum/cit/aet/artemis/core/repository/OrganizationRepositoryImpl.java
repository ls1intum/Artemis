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
 * Implementation of organization repository fragment with advanced filtering
 * and aggregation
 */
public class OrganizationRepositoryImpl implements OrganizationRepositoryCustom {

    private final EntityManager entityManager;

    public OrganizationRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<OrganizationDTO> getAllOrganizations(SearchTermPageableSearchDTO<String> search) {
        final String searchTerm = search.getSearchTerm();
        final int page = search.getPage();
        final int pageSize = search.getPageSize();
        final String sortedColumn = search.getSortedColumn();
        final SortingOrder sortOrder = search.getSortingOrder();

        // Pageable here is only used for offset/limit (sorting is applied in
        // CriteriaQuery directly,
        // because we also support sorting by aggregated counts).
        final Pageable pageable = PageRequest.of(page, pageSize);

        Specification<Organization> specification = null;
        if (searchTerm != null && !searchTerm.isBlank()) {
            specification = getSearchTermSpecification(searchTerm);
        }

        return findAllWithAggregationAndSpecification(specification, pageable, sortedColumn, sortOrder);
    }

    /**
     * Find all organizations with aggregated counts of users and courses, applying
     * filtering and sorting (including aggregated sort fields).
     */
    private Page<OrganizationDTO> findAllWithAggregationAndSpecification(Specification<Organization> specification, Pageable pageable, String sortedColumn,
            SortingOrder sortOrder) {

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<OrganizationDTO> query = builder.createQuery(OrganizationDTO.class);
        Root<Organization> root = query.from(Organization.class);

        // Single set of joins reused for both select and sorting
        Join<Organization, User> userJoin = root.join(Organization_.USERS, JoinType.LEFT);
        Join<Organization, Course> courseJoin = root.join(Organization_.COURSES, JoinType.LEFT);

        Expression<Long> userCountExpr = builder.countDistinct(userJoin.get(User_.ID));
        Expression<Long> courseCountExpr = builder.countDistinct(courseJoin.get(Course_.ID));

        // DTO projection
        query.select(builder.construct(OrganizationDTO.class, root.get(Organization_.ID), root.get(Organization_.NAME), root.get(Organization_.SHORT_NAME),
                root.get(Organization_.EMAIL_PATTERN), userCountExpr, courseCountExpr));

        // Filtering
        Predicate predicate = null;
        if (specification != null) {
            predicate = specification.toPredicate(root, query, builder);
        }
        if (predicate != null) {
            query.where(predicate);
        }

        // Group by exactly the non-aggregated selected fields (portable JPQL/Criteria
        // behavior)
        query.groupBy(root.get(Organization_.ID), root.get(Organization_.NAME), root.get(Organization_.SHORT_NAME), root.get(Organization_.EMAIL_PATTERN));

        // Sorting
        Expression<?> sortExpr = switch (sortedColumn) {
            case "name" -> root.get(Organization_.NAME);
            case "shortName" -> root.get(Organization_.SHORT_NAME);
            case "emailPattern" -> root.get(Organization_.EMAIL_PATTERN);
            case "numberOfUsers" -> userCountExpr;
            case "numberOfCourses" -> courseCountExpr;
            case "id" -> root.get(Organization_.ID);
            default -> root.get(Organization_.ID);
        };

        Order primaryOrder = (sortOrder != null && sortOrder == SortingOrder.DESCENDING) ? builder.desc(sortExpr) : builder.asc(sortExpr);

        // Tie-breaker for stable pagination (especially when many orgs share same
        // count)
        Order tieBreaker = builder.asc(root.get(Organization_.ID));

        query.orderBy(primaryOrder, tieBreaker);

        // Execute with pagination
        TypedQuery<OrganizationDTO> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<OrganizationDTO> results = typedQuery.getResultList();

        // Total count (no joins; fast)
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

        Predicate predicate = null;
        if (specification != null) {
            predicate = specification.toPredicate(countRoot, countQuery, builder);
        }
        if (predicate != null) {
            countQuery.where(predicate);
        }

        return entityManager.createQuery(countQuery).getSingleResult();
    }
}
