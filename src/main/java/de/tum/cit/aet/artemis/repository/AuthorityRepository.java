package de.tum.cit.aet.artemis.repository;

import java.util.List;

import de.tum.cit.aet.artemis.domain.Authority;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

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
