package de.tum.in.www1.artemis.repository.hestia;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHintActivation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface ExerciseHintActivationRepository extends JpaRepository<ExerciseHintActivation, Long> {

    void deleteAllByUser(User user);

    @Query("""
            SELECT ueha FROM ExerciseHintActivation ueha
            LEFT JOIN FETCH ueha.exerciseHint eh
            LEFT JOIN FETCH eh.solutionEntries se
            WHERE ueha.exerciseHint.exercise.id = :exerciseId
            AND ueha.user.id = :userId
            """)
    Set<ExerciseHintActivation> findByExerciseAndUserWithExerciseHintRelations(@Param("exerciseId") long exerciseId, @Param("userId") long userId);

    @Query("""
            SELECT ueha FROM ExerciseHintActivation ueha
            WHERE ueha.exerciseHint.id = :exerciseHintId
            AND ueha.user.id = :userId
            """)
    Optional<ExerciseHintActivation> findByExerciseHintAndUser(@Param("exerciseHintId") long exerciseHintId, @Param("userId") long userId);

    default ExerciseHintActivation findByExerciseHintAndUserElseThrow(long exerciseHintId, long userId) {
        var optionalReport = findByExerciseHintAndUser(exerciseHintId, userId);
        return optionalReport.orElseThrow(() -> new EntityNotFoundException("ExerciseHintActivation", exerciseHintId + "-" + userId));
    }
}
