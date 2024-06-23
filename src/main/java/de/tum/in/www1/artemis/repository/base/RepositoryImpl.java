package de.tum.in.www1.artemis.repository.base;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import de.tum.in.www1.artemis.domain.DomainObject_;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class RepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;

    /**
     * Creates a new {@link SimpleJpaRepository} to manage objects of the given {@link JpaEntityInformation}.
     *
     * @param entityInformation the information of the entity
     * @param entityManager     the {@link EntityManager} to be used
     */
    public RepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
    }

    /**
     * Creates a new {@link SimpleJpaRepository} to manage objects of the given domain type.
     *
     * @param domainClass   the class of the domain type to manage
     * @param entityManager the {@link EntityManager} to be used
     */
    public RepositoryImpl(Class<T> domainClass, EntityManager entityManager) {
        this(JpaEntityInformationSupport.getEntityInformation(domainClass, entityManager), entityManager);
    }

    /**
     * Find an entity by its id and given specification or throw an EntityNotFoundException if it does not exist.
     *
     * @param specification the specification to apply
     * @param id            the id of the entity to find
     * @return the entity with the given id
     */
    @NotNull
    public T findOneByIdElseThrow(final Specification<T> specification, ID id) {
        return this.findOneById(specification, id).orElseThrow(() -> new EntityNotFoundException(entityInformation.getEntityName(), String.valueOf(id)));
    }

    /**
     * Get the entity if it exists or throw an EntityNotFoundException.
     *
     * @param optional the optional to get the entity from
     * @return the entity if it exists
     */
    @NotNull
    public T getValueElseThrow(Optional<T> optional) {
        return optional.orElseThrow(() -> new EntityNotFoundException(entityInformation.getEntityName()));
    }

    /**
     * Get the entity if it exists or throw an EntityNotFoundException.
     *
     * @param optional the optional to get the entity from
     * @param id       the id of the entity to find
     * @return the entity if it exists
     */
    @NotNull
    public T getValueElseThrow(Optional<T> optional, ID id) {
        return optional.orElseThrow(() -> new EntityNotFoundException(entityInformation.getEntityName(), String.valueOf(id)));
    }

    /**
     * Find an entity by its id or throw an EntityNotFoundException if it does not exist.
     *
     * @param id the id of the entity to find
     * @return the entity with the given id
     */
    @NotNull
    public T findByIdElseThrow(ID id) {
        return getValueElseThrow(findById(id), id);
    }

    /**
     * Find an entity by its id and given specification without using limiting internally.
     *
     * @param spec the specification to apply
     * @param id   the id of the base entity to find
     * @return the entity with the given id
     */
    @NotNull
    public Optional<T> findOneById(Specification<T> spec, ID id) {
        try {
            final Specification<T> hasIdSpec = (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(DomainObject_.ID), id);
            return Optional.of(this.getQuery(spec.and(hasIdSpec), Sort.unsorted()).getSingleResult());
        }
        catch (NoResultException noResultException) {
            return Optional.empty();
        }
    }

    /**
     * Find an entity by given specification without using limiting internally.
     *
     * @param spec the specification to apply
     * @return the entity that satisfies the given specification
     */
    @NotNull
    public Optional<T> findOneBySpec(Specification<T> spec) {
        try {
            return Optional.of(this.getQuery(spec, Sort.unsorted()).getSingleResult());
        }
        catch (NoResultException noResultException) {
            return Optional.empty();
        }
    }
}
