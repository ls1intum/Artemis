package de.tum.cit.aet.artemis.exercise.repository;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;

/**
 * Spring Data JPA repository for the Exercise entity for Tests.
 */
@Primary
@Lazy
@Repository
public interface ExerciseVersionTestRepository extends ExerciseVersionRepository {

    List<ExerciseVersion> findAllByExerciseId(Long exerciseId);
}
