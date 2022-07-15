package de.tum.in.www1.artemis.repository.hestia;

import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the ExerciseHint entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ExerciseHintRepository extends JpaRepository<ExerciseHint, Long> {

    @Query("""
            SELECT h
            FROM ExerciseHint h
            LEFT JOIN FETCH h.solutionEntries se
            WHERE h.id = :hintId
            """)
    Optional<ExerciseHint> findByIdWithRelations(Long hintId);

    @NotNull
    default ExerciseHint findByIdWithRelationsElseThrow(long hintId) throws EntityNotFoundException {
        return findByIdWithRelations(hintId).orElseThrow(() -> new EntityNotFoundException("Exercise Hint", hintId));
    }

    @NotNull
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
    Set<ExerciseHint> findByExerciseIdWithRelations(Long exerciseId);

    Set<ExerciseHint> findByTaskId(Long taskId);
}
