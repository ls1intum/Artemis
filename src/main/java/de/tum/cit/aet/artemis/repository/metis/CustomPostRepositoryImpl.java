package de.tum.cit.aet.artemis.repository.metis;

import java.util.Collections;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import de.tum.cit.aet.artemis.domain.metis.Post;
import de.tum.cit.aet.artemis.domain.metis.Post_;

public class CustomPostRepositoryImpl implements CustomPostRepository {

    private final EntityManager entityManager;

    public CustomPostRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Long> findPostIdsWithSpecification(Specification<Post> specification, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        // Main query
        CriteriaQuery<Long> criteriaQuery = builder.createQuery(Long.class);
        Root<Post> root = criteriaQuery.from(Post.class);
        criteriaQuery.select(root.get(Post_.ID));

        Predicate predicate = specification.toPredicate(root, criteriaQuery, builder);
        if (predicate != null) {
            criteriaQuery.where(predicate);
        }

        // Note: we do not sort, because this should be part of the specification

        TypedQuery<Long> query = entityManager.createQuery(criteriaQuery);
        if (pageable.isUnpaged()) {
            return new PageImpl<>(query.getResultList());
        }
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<Long> postIds = query.getResultList();

        // Count query
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Post> countRoot = countQuery.from(Post.class);
        Predicate countPredicate = specification.toPredicate(countRoot, countQuery, builder);
        if (countPredicate != null) {
            countQuery.where(countPredicate);
        }
        countQuery.select(builder.count(countRoot));

        // Remove all Orders the Specifications might have applied
        countQuery.orderBy(Collections.emptyList());

        Long countResult = entityManager.createQuery(countQuery).getSingleResult();
        long count = countResult != null ? countResult : 0L;

        return new PageImpl<>(postIds, pageable, count);
    }
}
