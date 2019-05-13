package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Authority;

/**
 * Spring Data JPA repository for the Authority entity.
 */
public interface AuthorityRepository extends JpaRepository<Authority, String> {
}
