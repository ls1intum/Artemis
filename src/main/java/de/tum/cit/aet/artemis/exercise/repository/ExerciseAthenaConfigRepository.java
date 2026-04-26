package de.tum.cit.aet.artemis.exercise.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;

/**
 * Spring Data JPA repository for the ExerciseAthenaConfig entity.
 */
@Repository
public interface ExerciseAthenaConfigRepository extends ArtemisJpaRepository<ExerciseAthenaConfig, Long> {

    /**
     * Find the Athena configuration for a given exercise.
     *
     * @param exerciseId the ID of the exercise
     * @return the Athena configuration for the exercise, or empty if not found
     */
    Optional<ExerciseAthenaConfig> findByExerciseId(Long exerciseId);

    /**
     * Delete the Athena configuration for a given exercise.
     *
     * @param exerciseId the ID of the exercise
     */
    @Modifying
    @Transactional // ok because of delete
    void deleteByExerciseId(Long exerciseId);
}
