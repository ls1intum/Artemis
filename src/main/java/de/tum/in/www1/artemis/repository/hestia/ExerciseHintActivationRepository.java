package de.tum.in.www1.artemis.repository.hestia;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.hestia.ExerciseHintActivation;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface ExerciseHintActivationRepository extends ArtemisJpaRepository<ExerciseHintActivation, Long> {

    @Query("""
            SELECT hintActivation
            FROM ExerciseHintActivation hintActivation
                LEFT JOIN FETCH hintActivation.exerciseHint hint
                LEFT JOIN FETCH hint.solutionEntries
            WHERE hintActivation.exerciseHint.exercise.id = :exerciseId
                AND hintActivation.user.id = :userId
            """)
    Set<ExerciseHintActivation> findByExerciseAndUserWithExerciseHintRelations(@Param("exerciseId") long exerciseId, @Param("userId") long userId);

    @Query("""
            SELECT hintActivation
            FROM ExerciseHintActivation hintActivation
            WHERE hintActivation.exerciseHint.id = :exerciseHintId
                AND hintActivation.user.id = :userId
            """)
    Optional<ExerciseHintActivation> findByExerciseHintAndUser(@Param("exerciseHintId") long exerciseHintId, @Param("userId") long userId);

    default ExerciseHintActivation findByExerciseHintAndUserElseThrow(long exerciseHintId, long userId) {
        var optionalReport = findByExerciseHintAndUser(exerciseHintId, userId);
        return optionalReport.orElseThrow(() -> new EntityNotFoundException("ExerciseHintActivation", exerciseHintId + "-" + userId));
    }
}
