package de.tum.cit.aet.artemis.core.repository.base;

import java.util.Collection;
import java.util.List;

import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * A repository that provides methods for fetching entities dynamically using {@link FetchOptions}.
 *
 * @param <T> the type of the entity
 */
@NoRepositoryBean
public interface DynamicSpecificationRepository<T, ID, F extends FetchOptions> extends ArtemisJpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * Find an entity by its id and given specification or throw an EntityNotFoundException if it does not exist.
     * Implemented in {@link RepositoryImpl#findOneByIdElseThrow(Specification, ID)}.
     *
     * @param specification the specification to apply
     * @param id            the id of the entity to find
     * @return the entity with the given id
     */
    @NotNull
    T findOneByIdElseThrow(final Specification<T> specification, ID id);

    /**
     * Finds an entity by its ID with optional dynamic fetching of associated entities.
     * Throws an {@link EntityNotFoundException} if the entity with the specified ID does not exist.
     *
     * @param id           the ID of the entity to find.
     * @param fetchOptions a collection of fetch options specifying the entities to fetch dynamically.
     * @return the entity with the specified ID and the associated entities fetched according to the provided options.
     */
    @NotNull
    default T findByIdWithDynamicFetchElseThrow(ID id, F... fetchOptions) {
        return findByIdWithDynamicFetchElseThrow(id, List.of(fetchOptions));
    }

    /**
     * Finds an entity by its ID with optional dynamic fetching of associated entities.
     * Throws an {@link EntityNotFoundException} if the entity with the specified ID does not exist.
     *
     * @param id           the ID of the entity to find.
     * @param fetchOptions a collection of fetch options specifying the entities to fetch dynamically.
     * @return the entity with the specified ID and the associated entities fetched according to the provided options.
     */
    @NotNull
    default T findByIdWithDynamicFetchElseThrow(ID id, Collection<F> fetchOptions) {
        var specification = getDynamicSpecification(fetchOptions);
        return findOneByIdElseThrow(specification, id);
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
