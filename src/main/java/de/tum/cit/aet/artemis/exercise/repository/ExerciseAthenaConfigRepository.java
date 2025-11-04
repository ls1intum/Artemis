package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;

/**
 * Repository for {@link ExerciseAthenaConfig}.
 */
@Profile({ PROFILE_CORE, PROFILE_ATHENA })
@Lazy
@Repository
public interface ExerciseAthenaConfigRepository extends ArtemisJpaRepository<ExerciseAthenaConfig, Long> {

    @Query("""
            SELECT cfg
            FROM ExerciseAthenaConfig cfg
            WHERE cfg.exercise.id = :exerciseId
            """)
    Optional<ExerciseAthenaConfig> findByExerciseId(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT cfg.exercise
            FROM ExerciseAthenaConfig cfg
            WHERE cfg.feedbackSuggestionModule IS NOT NULL
                AND cfg.exercise.dueDate > :dueDate
            """)
    Set<Exercise> findExercisesWithFeedbackSuggestionModuleAndDueDateAfter(@Param("dueDate") ZonedDateTime dueDate);

    @Query("""
            SELECT cfg
            FROM ExerciseAthenaConfig cfg
            WHERE cfg.exercise.id IN :exerciseIds
            """)
    List<ExerciseAthenaConfig> findAllByExerciseIds(@Param("exerciseIds") Set<Long> exerciseIds);

    @Transactional
    @Modifying
    @Query("""
            DELETE
            FROM ExerciseAthenaConfig cfg
            WHERE cfg.exercise.id = :exerciseId
            """)
    void deleteByExerciseId(@Param("exerciseId") long exerciseId);
}
