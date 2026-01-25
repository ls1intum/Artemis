package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseVersionMetadataDTO;

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

    /**
     * Finds all exercise versions as DTOs for a given exercise ID.
     * This query only fetches the necessary fields (id, user info, createdDate) and avoids
     * loading the large exerciseSnapshot JSON column for optimal performance.
     *
     * @param exerciseId the ID of the exercise
     * @param pageable   the pagination information for the query
     * @return Paged ExerciseVersionDTO objects with user information
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseVersionMetadataDTO(
                ev.id,
                u,
                ev.createdDate
            )
            FROM ExerciseVersion ev
                JOIN User u ON u.id = ev.authorId
            WHERE ev.exerciseId = :exerciseId
            ORDER BY ev.createdDate DESC
            """)
    Page<ExerciseVersionMetadataDTO> findAllByExerciseIdOrderByCreatedDateDesc(@Param("exerciseId") Long exerciseId, Pageable pageable);

}
