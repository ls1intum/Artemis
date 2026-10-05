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
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;

/**
 * Spring Data JPA repository for the {@link PlagiarismDetectionConfig} entity. The configuration holds the key to its
 * exercise and the exercise carries no mapped association to it, so loading an exercise never reads the configuration. A
 * flow that needs it reads it here and may attach it to the exercise it already holds with {@link #attachTo(Exercise)} or
 * {@link #attachTo(Collection)}. Every exercise owns exactly one row, created together with the exercise by
 * {@link #insertDefaultsFor(long)}; a flow that changes the settings updates that row in place with
 * {@link #applyTo(Exercise, PlagiarismDetectionConfig)}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PlagiarismDetectionConfigRepository extends ArtemisJpaRepository<PlagiarismDetectionConfig, Long> {

    /** The number of exercises whose configurations are read by one query when attaching them in bulk. */
    int ATTACH_BATCH_SIZE = 1000;

    /**
     * Finds the plagiarism detection configuration of the given exercise, if one exists.
     *
     * @param exerciseId the id of the exercise
     * @return the configuration, or empty when the exercise has none
     */
    @Query("""
            SELECT configuration
            FROM PlagiarismDetectionConfig configuration
            WHERE configuration.exercise.id = :exerciseId
            """)
    Optional<PlagiarismDetectionConfig> findByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Reads the configurations of several exercises in one query.
     *
     * @param exerciseIds the ids of the exercises
     * @return the configurations that exist; an exercise without one has no entry, and
     *         {@link PlagiarismDetectionConfig#getExerciseId()} says which exercise each one belongs to
     */
    @Query("""
            SELECT configuration
            FROM PlagiarismDetectionConfig configuration
            WHERE configuration.exercise.id IN :exerciseIds
            """)
    List<PlagiarismDetectionConfig> findAllByExerciseIdIn(@Param("exerciseIds") Collection<Long> exerciseIds);

    /**
     * Creates the plagiarism detection settings of a newly stored exercise with the defaults, unless the exercise already
     * has them. The settings are permanent: they exist for every exercise and are only ever updated afterwards.
     *
     * @param exerciseId the id of the exercise
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query(value = """
            INSERT INTO plagiarism_detection_config (exercise_id, continuous_plagiarism_control_enabled, continuous_plagiarism_control_post_due_date_checks_enabled,
                continuous_plagiarism_control_case_student_response_period, similarity_threshold, minimum_score, minimum_size)
            SELECT exercise.id, FALSE, FALSE, 7, 90, 0, 50 FROM exercise
            WHERE exercise.id = :exerciseId
                AND NOT EXISTS (SELECT 1 FROM plagiarism_detection_config existing WHERE existing.exercise_id = exercise.id)
            """, nativeQuery = true)
    void insertDefaultsFor(@Param("exerciseId") long exerciseId);

    /**
     * Changes an exercise's permanent plagiarism detection settings in place.
     *
     * @param exerciseId               the id of the exercise
     * @param controlEnabled           whether continuous plagiarism control is enabled
     * @param postDueDateChecksEnabled whether continuous plagiarism control also checks after the due date
     * @param studentResponsePeriod    the days a student has to respond to a plagiarism case
     * @param similarityThreshold      the similarity from which submissions are reported
     * @param minimumScore             the score a submission needs to be considered
     * @param minimumSize              the size a submission needs to be considered
     * @return the number of updated rows: 1, or 0 if the exercise has no settings row
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("""
            UPDATE PlagiarismDetectionConfig configuration
            SET configuration.continuousPlagiarismControlEnabled = :controlEnabled,
                configuration.continuousPlagiarismControlPostDueDateChecksEnabled = :postDueDateChecksEnabled,
                configuration.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod = :studentResponsePeriod,
                configuration.similarityThreshold = :similarityThreshold, configuration.minimumScore = :minimumScore, configuration.minimumSize = :minimumSize
            WHERE configuration.exercise.id = :exerciseId
            """)
    int updateSettings(@Param("exerciseId") long exerciseId, @Param("controlEnabled") boolean controlEnabled, @Param("postDueDateChecksEnabled") boolean postDueDateChecksEnabled,
            @Param("studentResponsePeriod") int studentResponsePeriod, @Param("similarityThreshold") int similarityThreshold, @Param("minimumScore") int minimumScore,
            @Param("minimumSize") int minimumSize);

    /**
     * Puts the configuration of an exercise onto it, for a flow that reads or maps it through the exercise. An exercise
     * without a configuration is left empty, which reads as "no plagiarism detection configuration".
     *
     * @param exercise the exercise, may be null
     */
    default void attachTo(@Nullable Exercise exercise) {
        if (exercise != null && exercise.getId() != null) {
            exercise.setPlagiarismDetectionConfig(findByExerciseId(exercise.getId()).orElse(null));
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
        exercisesById.values().forEach(exercise -> exercise.setPlagiarismDetectionConfig(null));
        List<Long> exerciseIds = List.copyOf(exercisesById.keySet());
        for (int from = 0; from < exerciseIds.size(); from += ATTACH_BATCH_SIZE) {
            List<Long> batch = exerciseIds.subList(from, Math.min(from + ATTACH_BATCH_SIZE, exerciseIds.size()));
            findAllByExerciseIdIn(batch).forEach(configuration -> exercisesById.get(configuration.getExerciseId()).setPlagiarismDetectionConfig(configuration));
        }
    }

    /**
     * Gives a newly stored exercise its permanent plagiarism detection row and applies the requested settings to it. Calling
     * this again for the same exercise is safe.
     *
     * @param exercise  the exercise that was just stored
     * @param requested the settings the creating request carried, or null for the defaults
     * @return the stored settings
     */
    default PlagiarismDetectionConfig initializeFor(Exercise exercise, @Nullable PlagiarismDetectionConfig requested) {
        insertDefaultsFor(exercise.getId());
        return applyTo(exercise, requested);
    }

    /**
     * Applies the requested plagiarism detection settings to a saved exercise's permanent row and leaves the stored settings
     * on the exercise's slot. The row is updated in place and never replaced; requesting nothing keeps what is stored.
     *
     * @param exercise  the saved exercise
     * @param requested the settings the exercise should have afterwards, or null to keep the stored ones
     * @return the stored settings
     * @throws EntityNotFoundException if the exercise has no settings row, which means it was stored without its defaults
     */
    default PlagiarismDetectionConfig applyTo(Exercise exercise, @Nullable PlagiarismDetectionConfig requested) {
        if (requested != null && updateSettings(exercise.getId(), requested.isContinuousPlagiarismControlEnabled(),
                requested.isContinuousPlagiarismControlPostDueDateChecksEnabled(), requested.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(),
                requested.getSimilarityThreshold(), requested.getMinimumScore(), requested.getMinimumSize()) != 1) {
            throw new EntityNotFoundException("PlagiarismDetectionConfig", exercise.getId());
        }
        attachTo(exercise);
        PlagiarismDetectionConfig stored = exercise.getPlagiarismDetectionConfig();
        if (stored == null) {
            throw new EntityNotFoundException("PlagiarismDetectionConfig", exercise.getId());
        }
        return stored;
    }
}
