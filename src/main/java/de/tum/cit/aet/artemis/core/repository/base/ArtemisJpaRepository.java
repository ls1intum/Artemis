package de.tum.cit.aet.artemis.core.repository.base;

import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * A repository that provides methods for fetching entities dynamically using {@link FetchOptions}.
 *
 * @param <T> the type of the entity
 */
@NoRepositoryBean
public interface ArtemisJpaRepository<T, ID> extends JpaRepository<T, ID> {

    /**
     * Get the entity if it exists or throw an EntityNotFoundException.
     * Implemented in {@link RepositoryImpl#getValueElseThrow(Optional)}.
     *
     * @param <U>      the type or a subclass of the entity
     * @param optional the optional to get the entity from
     * @return the entity if it exists
     */
    <U extends T> U getValueElseThrow(Optional<U> optional);

    /**
     * Get the entity if it exists or throw an EntityNotFoundException.
     * Implemented in {@link RepositoryImpl#getValueElseThrow(Optional, ID)}.
     *
     * @param <U>      the type or a subclass of the entity
     * @param optional the optional to get the entity from
     * @param id       the id of the entity to find
     * @return the entity if it exists
     */
    <U extends T> U getValueElseThrow(Optional<U> optional, ID id);

    /**
     * Get an arbitrary value if it exists or throw an EntityNotFoundException.
     * Implemented in {@link RepositoryImpl#getArbitraryValueElseThrow(Optional)}.
     *
     * @param <U>      the type of the entity
     * @param optional the optional to get the entity from
     * @return the entity if it exists
     */
    <U> U getArbitraryValueElseThrow(Optional<U> optional);

    /**
     * Get an arbitrary value if it exists or throw an EntityNotFoundException.
     * Implemented in {@link RepositoryImpl#getArbitraryValueElseThrow(Optional, String)}.
     *
     * @param <U>      the type of the entity
     * @param optional the optional to get the entity from
     * @param id       the id of the entity to find in string representation
     * @return the entity if it exists
     */
    <U> U getArbitraryValueElseThrow(Optional<U> optional, String id);

    /**
     * Find an entity by its id or throw an EntityNotFoundException if it does not exist.
     * Implemented in {@link RepositoryImpl#findByIdElseThrow(ID)}.
     *
     * @param id the id of the entity to find
     * @return the entity with the given id
     */
    T findByIdElseThrow(ID id);

    /**
     * Find an entity by its id and given specification without using limiting internally.
     *
     * @param spec the specification to apply
     * @param id   the id of the entity to find, it will augment spec with an <bold>and</bold> operator
     * @return the entity that corresponds to spec and has the given id
     */
    Optional<T> findOneById(Specification<T> spec, ID id);

    /**
     * Find an entity by given specification without using limiting internally.
     *
     * @param spec the specification to apply
     * @return the entity that satisfies the given specification
     */
    Optional<T> findOneBySpec(Specification<T> spec);
}
