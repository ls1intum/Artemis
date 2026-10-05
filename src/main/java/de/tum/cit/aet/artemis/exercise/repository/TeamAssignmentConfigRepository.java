package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;

/**
 * Spring Data JPA repository for the {@link TeamAssignmentConfig} entity. The configuration holds the key to its exercise
 * and the exercise carries no mapped association to it, so loading an exercise never reads the configuration. A flow that
 * needs it reads it here and may attach it to the exercise it already holds with {@link #attachTo(Exercise)} or
 * {@link #attachTo(Collection)}; a flow that writes it goes through {@link #replaceFor(Exercise, TeamAssignmentConfig)}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface TeamAssignmentConfigRepository extends ArtemisJpaRepository<TeamAssignmentConfig, Long> {

    /** The number of exercises whose configurations are read by one query when attaching them in bulk. */
    int ATTACH_BATCH_SIZE = 1000;

    /**
     * Finds the team assignment configuration of the given exercise, if one exists.
     *
     * @param exerciseId the id of the exercise
     * @return the configuration, or empty when the exercise is not a team exercise
     */
    @Query("""
            SELECT configuration
            FROM TeamAssignmentConfig configuration
            WHERE configuration.exercise.id = :exerciseId
            """)
    Optional<TeamAssignmentConfig> findByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Reads the configurations of several exercises in one query.
     *
     * @param exerciseIds the ids of the exercises
     * @return the configurations that exist; an exercise without one has no entry, and
     *         {@link TeamAssignmentConfig#getExerciseId()} says which exercise each one belongs to
     */
    @Query("""
            SELECT configuration
            FROM TeamAssignmentConfig configuration
            WHERE configuration.exercise.id IN :exerciseIds
            """)
    List<TeamAssignmentConfig> findAllByExerciseIdIn(@Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Removes the team assignment configuration of an exercise, e.g. when team mode is switched off.
     *
     * @param exerciseId the id of the exercise
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM TeamAssignmentConfig configuration
            WHERE configuration.exercise.id = :exerciseId
            """)
    void deleteByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Puts the configuration of an exercise onto it, for a flow that reads or maps it through the exercise. An exercise
     * without a configuration is left empty, which reads as "not a team exercise".
     *
     * @param exercise the exercise, may be null
     */
    default void attachTo(@Nullable Exercise exercise) {
        if (exercise != null && exercise.getId() != null) {
            exercise.setTeamAssignmentConfig(findByExerciseId(exercise.getId()).orElse(null));
        }
    }

    /**
     * Puts the configurations of several exercises onto them with one query per {@value #ATTACH_BATCH_SIZE} exercises,
     * so that a list of exercises does not read one configuration per exercise.
     *
     * @param exercises the exercises to attach the configurations to
     */
    default void attachTo(Collection<? extends Exercise> exercises) {
        Map<Long, Exercise> exercisesById = new HashMap<>();
        exercises.stream().filter(exercise -> exercise.getId() != null).forEach(exercise -> exercisesById.put(exercise.getId(), exercise));
        exercisesById.values().forEach(exercise -> exercise.setTeamAssignmentConfig(null));
        List<Long> exerciseIds = List.copyOf(exercisesById.keySet());
        for (int from = 0; from < exerciseIds.size(); from += ATTACH_BATCH_SIZE) {
            List<Long> batch = exerciseIds.subList(from, Math.min(from + ATTACH_BATCH_SIZE, exerciseIds.size()));
            findAllByExerciseIdIn(batch).forEach(configuration -> exercisesById.get(configuration.getExerciseId()).setTeamAssignmentConfig(configuration));
        }
    }

    /**
     * Makes the stored team assignment configuration of a saved exercise equal to the desired one, and leaves the stored
     * configuration on the exercise's slot. The exercise has to be saved already, because the configuration holds the key
     * to it.
     * <p>
     * A configuration that exists is updated in place and a replaced one is never left behind; null removes it, which is
     * what switching team mode off means.
     *
     * @param exercise the saved exercise
     * @param desired  the configuration the exercise should have afterwards, or null for none
     * @return the stored configuration, or null when the exercise has none
     */
    default @Nullable TeamAssignmentConfig replaceFor(Exercise exercise, @Nullable TeamAssignmentConfig desired) {
        if (desired == null) {
            deleteByExerciseId(exercise.getId());
            exercise.setTeamAssignmentConfig(null);
            return null;
        }
        TeamAssignmentConfig stored = findByExerciseId(exercise.getId()).orElseGet(TeamAssignmentConfig::new);
        stored.setMinTeamSize(desired.getMinTeamSize());
        stored.setMaxTeamSize(desired.getMaxTeamSize());
        stored.setExercise(exercise);
        stored = save(stored);
        exercise.setTeamAssignmentConfig(stored);
        return stored;
    }
}
