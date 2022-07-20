package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Authority;

/**
 * Spring Data JPA repository for the Authority entity.
 */
public interface AuthorityRepository extends JpaRepository<Authority, String> {

    /**
     * @return an unmodifiable list of all the authorities
     */
    default List<String> getAuthorities() {
        return findAll().stream().map(Authority::getName).toList();
    }
}
