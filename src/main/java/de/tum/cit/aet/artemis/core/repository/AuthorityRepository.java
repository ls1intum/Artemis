package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Authority entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AuthorityRepository extends ArtemisJpaRepository<Authority, String> {

    /**
     * @return an unmodifiable list of all the authorities
     */
    default List<String> getAuthorities() {
        return findAll().stream().map(Authority::getName).toList();
    }
}
