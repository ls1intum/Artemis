package de.tum.cit.aet.artemis.exercise.repository;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;

/**
 * Spring Data JPA repository for the ExerciseVersion entity.
 */
@Repository
public interface ExerciseVersionRepository extends ArtemisJpaRepository<ExerciseVersion, Long> {
    // Basic CRUD operations are inherited from ArtemisJpaRepository
}
