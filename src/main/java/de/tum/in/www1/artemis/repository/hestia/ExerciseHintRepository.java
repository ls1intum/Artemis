package de.tum.in.www1.artemis.repository.hestia;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Nonnull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the ExerciseHint entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ExerciseHintRepository extends JpaRepository<ExerciseHint, Long> {

    @Query("""
            SELECT h
            FROM ExerciseHint h
                LEFT JOIN FETCH h.solutionEntries se
            WHERE h.id = :hintId
            """)
    Optional<ExerciseHint> findByIdWithRelations(@Param("hintId") Long hintId);

    @Nonnull
    default ExerciseHint findByIdWithRelationsElseThrow(long hintId) throws EntityNotFoundException {
        return findByIdWithRelations(hintId).orElseThrow(() -> new EntityNotFoundException("Exercise Hint", hintId));
    }

    @Nonnull
    default ExerciseHint findByIdElseThrow(long exerciseHintId) throws EntityNotFoundException {
        return findById(exerciseHintId).orElseThrow(() -> new EntityNotFoundException("Exercise Hint", exerciseHintId));
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
