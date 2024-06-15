package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.domain.DomainObject_;
import de.tum.in.www1.artemis.repository.fetchOptions.FetchOptions;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * A repository that provides methods for fetching entities dynamically using {@link FetchOptions}.
 *
 * @param <T> the type of the entity
 */
public interface DynamicSpecificationRepository<T> {

    /**
     * Find an entity by applying the given specification.
     * Provided by the concrete repository implementation. Do not override.
     *
     * @param spec the specification to apply
     * @return the entity that matches the specification
     */
    Optional<T> findOne(Specification<T> spec);

    /**
     * Find an entity by its id or throw an EntityNotFoundException if it does not exist.
     *
     * @param specification the specification to apply
     * @param id            the id of the entity to find
     * @return the entity with the given id
     */
    default T findOneByIdElseThrow(Specification<T> specification, long id, String entityName) {
        final Specification<T> hasIdSpec = (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(DomainObject_.ID), id);
        return findOne(specification.and(hasIdSpec)).orElseThrow(() -> new EntityNotFoundException(entityName, id));
    }

    /**
     * Create a Specification for dynamic fetching based on the provided fetch options.
     *
     * @param fetchOptions a collection of fetch options specifying the entities to fetch dynamically
     * @return a specification for dynamic fetching based on the provided fetch options
     */
    @NotNull
    default Specification<T> getDynamicSpecification(Collection<? extends FetchOptions> fetchOptions) {
        return (root, query, criteriaBuilder) -> {
            for (var option : fetchOptions) {
                var fetchPath = option.getFetchPath().split("\\.");
                var partialFetch = root.fetch(fetchPath[0], JoinType.LEFT);
                for (int i = 1; i < fetchPath.length; i++) {
                    String partialFetchPath = fetchPath[i];
                    partialFetch = partialFetch.fetch(partialFetchPath, JoinType.LEFT);
                }
            }
            return null;
        };
    }
}
