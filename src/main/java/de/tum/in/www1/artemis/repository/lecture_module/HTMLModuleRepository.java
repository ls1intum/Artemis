package de.tum.in.www1.artemis.repository.lecture_module;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture_module.HTMLModule;

/**
 * Spring Data JPA repository for the HTML Module entity.
 */
@Repository
public interface HTMLModuleRepository extends JpaRepository<HTMLModule, Long> {
}
