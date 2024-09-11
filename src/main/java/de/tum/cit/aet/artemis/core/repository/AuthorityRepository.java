package de.tum.cit.aet.artemis.core.repository;

import java.util.List;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.Authority;

/**
 * Spring Data JPA repository for the Authority entity.
 */
public interface AuthorityRepository extends ArtemisJpaRepository<Authority, String> {

    /**
     * @return an unmodifiable list of all the authorities
     */
    default List<String> getAuthorities() {
        return findAll().stream().map(Authority::getName).toList();
    }
}
