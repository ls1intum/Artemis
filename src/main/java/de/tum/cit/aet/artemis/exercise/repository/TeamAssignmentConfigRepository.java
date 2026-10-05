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

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;

/**
 * Spring Data JPA repository for the {@link TeamAssignmentConfig} entity. The configuration holds the key to its exercise
 * and the exercise carries no mapped association to it, so loading an exercise never reads the configuration. A flow that
 * needs it reads it here and may attach it to the exercise it already holds with {@link #attachTo(Exercise)} or
 * {@link #attachTo(Collection)}. Every exercise owns exactly one row, created together with the exercise by
 * {@link #insertDefaultsFor(long)}; a flow that changes the settings updates that row in place with
 * {@link #applyTo(Exercise, TeamAssignmentConfig)}.
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
     * @return the configuration, or empty if the exercise was stored without going through a creation path that gives it its row
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
     * Creates the team settings of a newly stored exercise with the defaults. A plain insert: callers check {@link #existsByExerciseId(long)} first, so no statement ever locks an
     * index range. The
     * settings are permanent: they exist for every exercise, whether or not it is in team mode, and are only ever updated
     * afterwards.
     *
     * @param exerciseId the id of the exercise
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO team_assignment_config (exercise_id, min_team_size, max_team_size)
            VALUES (:exerciseId, 1, 1)
            """, nativeQuery = true)
    void insertDefaultsFor(@Param("exerciseId") long exerciseId);

    /**
     * Whether the exercise already has its permanent settings row. A plain read: it takes no lock.
     *
     * @param exerciseId the id of the exercise
     * @return true if the row exists
     */
    boolean existsByExerciseId(long exerciseId);

    /**
     * Changes the team sizes of an exercise's permanent settings in place.
     *
     * @param exerciseId  the id of the exercise
     * @param minTeamSize the smallest allowed team
     * @param maxTeamSize the largest allowed team
     * @return the number of updated rows: 1, or 0 if the exercise has no settings row
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE TeamAssignmentConfig configuration
            SET configuration.minTeamSize = :minTeamSize, configuration.maxTeamSize = :maxTeamSize
            WHERE configuration.exercise.id = :exerciseId
            """)
    int updateSizes(@Param("exerciseId") long exerciseId, @Param("minTeamSize") Integer minTeamSize, @Param("maxTeamSize") Integer maxTeamSize);

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
     * Gives a newly stored exercise its permanent team settings row and applies the requested settings to it. Calling this
     * again for the same exercise is safe.
     *
     * @param exercise  the exercise that was just stored
     * @param requested the settings the creating request carried, or null for the defaults
     * @return the stored settings
     */
    default TeamAssignmentConfig initializeFor(Exercise exercise, @Nullable TeamAssignmentConfig requested) {
        if (!existsByExerciseId(exercise.getId())) {
            insertDefaultsFor(exercise.getId());
        }
        return applyTo(exercise, requested);
    }

    /**
     * Applies the requested team settings to a saved exercise's permanent row and leaves the stored settings on the
     * exercise's slot. The row is updated in place and never replaced; requesting nothing keeps what is stored.
     *
     * @param exercise  the saved exercise
     * @param requested the settings the exercise should have afterwards, or null to keep the stored ones
     * @return the stored settings
     * @throws EntityNotFoundException if the exercise has no settings row, which means it was stored without its defaults
     */
    default TeamAssignmentConfig applyTo(Exercise exercise, @Nullable TeamAssignmentConfig requested) {
        if (requested != null && updateSizes(exercise.getId(), requested.getMinTeamSize(), requested.getMaxTeamSize()) != 1) {
            throw new EntityNotFoundException("TeamAssignmentConfig", exercise.getId());
        }
        attachTo(exercise);
        TeamAssignmentConfig stored = exercise.getStoredTeamAssignmentConfig();
        if (stored == null) {
            throw new EntityNotFoundException("TeamAssignmentConfig", exercise.getId());
        }
        return stored;
    }
}
