package de.tum.in.www1.artemis.repository;

import java.util.Collection;

import jakarta.persistence.criteria.JoinType;
import jakarta.validation.constraints.NotNull;

import org.springframework.data.jpa.domain.Specification;

import de.tum.in.www1.artemis.repository.fetchOptions.FetchOptions;

/**
 * A repository that provides methods for fetching entities dynamically using {@link FetchOptions}.
 *
 * @param <T> the type of the entity
 */
public interface DynamicSpecificationRepository<T> {

    /**
     * Find an entity by its id or throw an EntityNotFoundException if it does not exist.
     * See {@link RepositoryImpl#findOneByIdElseThrow(Specification, long)} for the implementation.
     *
     * @param specification the specification to apply
     * @param id            the id of the entity to find
     * @return the entity with the given id
     */
    T findOneByIdElseThrow(Specification<T> specification, long id);

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
