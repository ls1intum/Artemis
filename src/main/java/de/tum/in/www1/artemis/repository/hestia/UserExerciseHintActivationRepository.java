package de.tum.in.www1.artemis.repository.hestia;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.hestia.UserExerciseHintActivation;

public interface UserExerciseHintActivationRepository extends JpaRepository<UserExerciseHintActivation, Long> {

    @Query("""
            SELECT ueha FROM UserExerciseHintActivation ueha
            WHERE ueha.exerciseHint.exercise.id = :exerciseId
            AND ueha.user.id = :userId
            """)
    Set<UserExerciseHintActivation> findByExerciseAndUser(@Param("exerciseId") long exerciseId, @Param("userId") long userId);

    @Query("""
            SELECT ueha FROM UserExerciseHintActivation ueha
            WHERE ueha.exerciseHint.id = :exerciseHintId
            AND ueha.user.id = :userId
            """)
    Optional<UserExerciseHintActivation> findByExerciseHintAndUser(@Param("exerciseHintId") long exerciseHintId, @Param("userId") long userId);
}
