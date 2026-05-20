package de.tum.cit.aet.artemis.core.repository.base;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * A repository that provides methods for fetching entities dynamically using {@link FetchOptions}.
 * <p>
 * Custom methods (e.g., {@code getValueElseThrow}, {@code findByIdElseThrow}) are declared
 * on the {@link ArtemisJpaRepositoryCustom} fragment interface and implemented by the
 * {@link RepositoryImpl} base class.
 *
 * @param <T>  the type of the entity
 * @param <ID> the type of the entity's identifier
 */
@NoRepositoryBean
public interface ArtemisJpaRepository<T, ID> extends JpaRepository<T, ID>, ArtemisJpaRepositoryCustom<T, ID> {
}
