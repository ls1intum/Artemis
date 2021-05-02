package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Authority;

/**
 * Spring Data JPA repository for the Authority entity.
 */
public interface AuthorityRepository extends JpaRepository<Authority, String> {

    /**
     * @return a list of all the authorities
     */
    default List<String> getAuthorities() {
        return findAll().stream().map(Authority::getName).collect(Collectors.toList());
    }
}
