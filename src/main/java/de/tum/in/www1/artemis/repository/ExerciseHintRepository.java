package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ExerciseHint;

/**
 * Spring Data  repository for the ExerciseHint entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseHintRepository extends JpaRepository<ExerciseHint, Long> {

}
