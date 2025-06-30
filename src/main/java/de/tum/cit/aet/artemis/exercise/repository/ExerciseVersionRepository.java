package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
}
