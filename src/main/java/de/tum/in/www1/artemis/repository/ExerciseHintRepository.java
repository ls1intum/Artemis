package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.ExerciseHint;

/**
 * Spring Data  repository for the ExerciseHint entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseHintRepository extends JpaRepository<ExerciseHint, Long> {

    Set<ExerciseHint> findByExerciseId(@Param("exerciseId") Long exerciseId);
}
