package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;

/**
 * Spring Data JPA repository for the ExerciseVersion entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExerciseVersionRepository extends ArtemisJpaRepository<ExerciseVersion, Long> {
    // Basic CRUD operations are inherited from ArtemisJpaRepository

    /**
     * Find the most recent version of an exercise by its ID
     * Uses JPQL with ordering by creation date
     *
     * @param exerciseId the ID of the exercise
     * @return the latest version of the exercise, or empty if no versions exist
     */
    Optional<ExerciseVersion> findTopByExerciseIdOrderByCreatedDateDesc(Long exerciseId);
}
