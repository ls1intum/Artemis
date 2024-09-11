package de.tum.cit.aet.artemis.programming.repository.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.hestia.ExerciseHint;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the ExerciseHint entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ExerciseHintRepository extends ArtemisJpaRepository<ExerciseHint, Long> {

    @Query("""
            SELECT h
            FROM ExerciseHint h
                LEFT JOIN FETCH h.solutionEntries se
            WHERE h.id = :hintId
            """)
    Optional<ExerciseHint> findByIdWithRelations(@Param("hintId") Long hintId);

    @NotNull
    default ExerciseHint findByIdWithRelationsElseThrow(long hintId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithRelations(hintId), hintId);
    }

    Set<ExerciseHint> findByExerciseId(Long exerciseId);

    @Query("""
            SELECT h
            FROM ExerciseHint h
                LEFT JOIN FETCH h.solutionEntries se
            WHERE h.exercise.id = :exerciseId
            """)
    Set<ExerciseHint> findByExerciseIdWithRelations(@Param("exerciseId") Long exerciseId);

    Set<ExerciseHint> findByTaskId(Long taskId);
}
