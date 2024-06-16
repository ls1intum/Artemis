package de.tum.in.www1.artemis.repository.base;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import de.tum.in.www1.artemis.domain.DomainObject_;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * A repository that provides methods for fetching entities dynamically using {@link FetchOptions}.
 *
 * @param <T> the type of the entity
 */
@NoRepositoryBean
public interface DynamicSpecificationRepository<T, ID, F extends FetchOptions> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * Find an entity by its id and given specification.
     *
     * @param specification the specification to apply
     * @param id            the id of the entity to find
     * @return the entity with the given id
     */
    default Optional<T> findOneById(Specification<T> specification, long id) {
        final Specification<T> hasIdSpec = (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(DomainObject_.ID), id);
        return findOne(specification.and(hasIdSpec));
    }

    /**
     * Find an entity by its id and given specification or throw an EntityNotFoundException if it does not exist.
     *
     * @param specification the specification to apply
     * @param id            the id of the entity to find
     * @param entityName    the name of the entity to find
     * @return the entity with the given id
     */
    default T findOneByIdElseThrow(Specification<T> specification, long id, String entityName) {
        return findOneById(specification, id).orElseThrow(() -> new EntityNotFoundException(entityName, id));
    }

    /**
     * Create a Specification for dynamic fetching based on the provided fetch options.
     *
     * @param fetchOptions a collection of fetch options specifying the entities to fetch dynamically
     * @return a specification for dynamic fetching based on the provided fetch options
     */
    @NotNull
    default Specification<T> getDynamicSpecification(Collection<F> fetchOptions) {
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
