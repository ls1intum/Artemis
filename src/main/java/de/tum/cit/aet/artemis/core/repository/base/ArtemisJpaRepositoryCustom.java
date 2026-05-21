package de.tum.cit.aet.artemis.core.repository.base;

import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Custom fragment interface for {@link ArtemisJpaRepository} that declares methods
 * implemented by the {@link RepositoryImpl} base class.
 * <p>
 * This fragment interface is necessary for Spring Data 4 (2025.1+), which requires
 * custom repository methods to be declared on a separate fragment interface when they
 * are implemented by the repository base class. Without this, Spring Data tries to
 * derive queries from method names like {@code getValueElseThrow}, resulting in
 * {@code PropertyReferenceException}.
 *
 * @param <T>  the type of the entity
 * @param <ID> the type of the entity's identifier
 */
@NoRepositoryBean
public interface ArtemisJpaRepositoryCustom<T, ID> {

    /**
     * Get the entity if it exists or throw an EntityNotFoundException.
     *
     * @param <U>      the type or a subclass of the entity
     * @param optional the optional to get the entity from
     * @return the entity if it exists
     */
    <U extends T> U getValueElseThrow(Optional<U> optional);

    /**
     * Get the entity if it exists or throw an EntityNotFoundException.
     *
     * @param <U>      the type or a subclass of the entity
     * @param optional the optional to get the entity from
     * @param id       the id of the entity to find
     * @return the entity if it exists
     */
    <U extends T> U getValueElseThrow(Optional<U> optional, ID id);

    /**
     * Get an arbitrary value if it exists or throw an EntityNotFoundException.
     *
     * @param <U>      the type of the entity
     * @param optional the optional to get the entity from
     * @return the entity if it exists
     */
    <U> U getArbitraryValueElseThrow(Optional<U> optional);

    /**
     * Get an arbitrary value if it exists or throw an EntityNotFoundException.
     *
     * @param <U>      the type of the entity
     * @param optional the optional to get the entity from
     * @param id       the id of the entity to find in string representation
     * @return the entity if it exists
     */
    <U> U getArbitraryValueElseThrow(Optional<U> optional, String id);

    /**
     * Find an entity by its id or throw an EntityNotFoundException if it does not exist.
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

    /**
     * Find an entity by its id and given specification or throw an EntityNotFoundException if it does not exist.
     *
     * @param specification the specification to apply
     * @param id            the id of the entity to find
     * @return the entity with the given id
     */
    T findOneByIdElseThrow(Specification<T> specification, ID id);

    /**
     * Find an entity by its id and given specification without using limiting internally or throw if none found.
     *
     * @param spec the specification to apply
     * @param id   the id of the entity to find, it will augment spec with an <bold>and</bold> operator
     * @return the entity that corresponds to spec and has the given id
     */
    T findOneByIdOrElseThrow(Specification<T> spec, ID id);

    /**
     * Find an entity by given specification without using limiting internally or throw if none found.
     *
     * @param spec the specification to apply
     * @return the entity that satisfies the given specification
     */
    T findOneBySpecOrElseThrow(Specification<T> spec);
}
