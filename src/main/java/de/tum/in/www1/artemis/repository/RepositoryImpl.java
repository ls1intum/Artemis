package de.tum.in.www1.artemis.repository;

import java.io.Serializable;
import java.util.Collections;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.util.Assert;

import de.tum.in.www1.artemis.domain.DomainObject_;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class RepositoryImpl<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;

    private final EntityManager em;

    /**
     * Creates a new {@link SimpleJpaRepository} to manage objects of the given {@link JpaEntityInformation}.
     *
     * @param entityInformation must not be {@literal null}.
     * @param entityManager     must not be {@literal null}.
     */
    public RepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.em = entityManager;
    }

    /**
     * Creates a new {@link SimpleJpaRepository} to manage objects of the given domain type.
     *
     * @param domainClass   must not be {@literal null}.
     * @param entityManager must not be {@literal null}.
     */
    public RepositoryImpl(Class<T> domainClass, EntityManager entityManager) {
        this(JpaEntityInformationSupport.getEntityInformation(domainClass, entityManager), entityManager);
    }

    /**
     * Find an entity by its id or throw an EntityNotFoundException if it does not exist.
     *
     * @param specification
     * @param id
     * @return
     */
    public T findOneByIdElseThrow(final Specification<T> specification, long id) {
        final Specification<T> hasIdSpec = (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(DomainObject_.ID), id);
        return findOne(specification.and(hasIdSpec)).orElseThrow(() -> new EntityNotFoundException(entityInformation.getEntityName(), id));
    }

    /**
     * Creates a new count query for the given {@link Specification}.
     *
     * @param spec        can be {@literal null}.
     * @param domainClass must not be {@literal null}.
     */
    @Override
    protected <S extends T> TypedQuery<Long> getCountQuery(Specification<S> spec, Class<S> domainClass) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);

        Root<S> root = applySpecificationToCriteria(spec, domainClass, query);

        // TODO: replace all left join fetch with left join recursively, e.g. using something like the following line
        // root.getFetches().forEach(fetch -> root.join(String.valueOf(fetch.getAttribute()), JoinType.LEFT));
        // Workaround: remove all fetches as they are not supported in count queries
        root.getFetches().clear();

        if (query.isDistinct()) {
            query.select(builder.countDistinct(root));
        }
        else {
            query.select(builder.count(root));
        }

        // Remove all Orders the Specifications might have applied
        query.orderBy(Collections.emptyList());

        return em.createQuery(query);
    }

    /**
     * Applies the given {@link Specification} to the given {@link CriteriaQuery}.
     *
     * @param spec        can be {@literal null}.
     * @param domainClass must not be {@literal null}.
     * @param query       must not be {@literal null}.
     */
    private <S, U extends T> Root<U> applySpecificationToCriteria(@Nullable Specification<U> spec, Class<U> domainClass, CriteriaQuery<S> query) {

        Assert.notNull(domainClass, "Domain class must not be null!");
        Assert.notNull(query, "CriteriaQuery must not be null!");

        Root<U> root = query.from(domainClass);

        if (spec == null) {
            return root;
        }

        CriteriaBuilder builder = em.getCriteriaBuilder();
        Predicate predicate = spec.toPredicate(root, query, builder);

        if (predicate != null) {
            query.where(predicate);
        }

        return root;
    }
}
