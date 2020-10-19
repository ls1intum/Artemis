package de.tum.in.www1.artemis.repository.lecture_unit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture_unit.ExerciseUnit;

/**
 * Spring Data JPA repository for the Exercise Unit entity.
 */
@Repository
public interface ExerciseUnitRepository extends JpaRepository<ExerciseUnit, Long> {
}
