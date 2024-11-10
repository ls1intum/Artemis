package de.tum.cit.aet.artemis.programming.repository.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.hestia.ExerciseHintActivation;

@Profile(PROFILE_CORE)
@Repository
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
        return getValueElseThrow(findByExerciseHintAndUser(exerciseHintId, userId));
    }
}
