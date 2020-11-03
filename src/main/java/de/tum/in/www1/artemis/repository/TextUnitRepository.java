package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.TextUnit;

/**
 * Spring Data JPA repository for the Text Unit entity.
 */
@Repository
public interface TextUnitRepository extends JpaRepository<TextUnit, Long> {
}
