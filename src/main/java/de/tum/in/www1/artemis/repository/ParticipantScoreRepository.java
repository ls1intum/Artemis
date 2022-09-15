package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Instant;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.statistics.ScoreDistribution;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementOverviewExerciseStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresAggregatedInformation;

@Repository
public interface ParticipantScoreRepository extends JpaRepository<ParticipantScore, Long> {

    void removeAllByExerciseId(Long exerciseId);

    @Transactional
    @Modifying
    @Query("""
            UPDATE ParticipantScore p
            SET p.lastResult = NULL, p.lastPoints = NULL, p.lastScore = NULL
            WHERE p.lastResult.id = :lastResultId
            """)
    // Do not update last modified date
    void clearLastResultByResultId(@Param("lastResultId") Long lastResultId);

    @Transactional
    @Modifying
    @Query("""
            UPDATE ParticipantScore p
            SET p.lastRatedResult = NULL, p.lastRatedPoints = NULL, p.lastRatedScore = NULL
            WHERE p.lastRatedResult.id = :lastResultId
            """)
    // Do not update last modified date
    void clearLastRatedResultByResultId(@Param("lastResultId") Long lastResultId);

    @Query("""
            SELECT p FROM ParticipantScore p
            LEFT JOIN FETCH p.exercise
            WHERE p.lastResult IS NULL
            OR p.lastRatedResult IS NULL
            """)
    List<ParticipantScore> findAllOutdatedWithExercise();

    @EntityGraph(type = LOAD, attributePaths = { "exercise", "lastResult", "lastRatedResult" })
    Optional<ParticipantScore> findParticipantScoreByLastRatedResult(Result result);

    @EntityGraph(type = LOAD, attributePaths = { "exercise", "lastResult", "lastRatedResult" })
    Optional<ParticipantScore> findParticipantScoresByLastResult(Result result);

    @NotNull
    @Override
    @EntityGraph(type = LOAD, attributePaths = { "exercise", "lastResult", "lastRatedResult" })
    List<ParticipantScore> findAll();

    List<ParticipantScore> findAllByExercise(Exercise exercise);

    @Query("""
                SELECT p
                FROM ParticipantScore p
                LEFT JOIN FETCH p.exercise
                WHERE p.lastResult.id = :resultId
                OR p.lastRatedResult.id = :resultId
            """)
    Optional<ParticipantScore> findByResultIdWithExercise(@Param("resultId") Long resultId);

    @Query("""
            SELECT p
            FROM ParticipantScore p LEFT JOIN FETCH p.exercise LEFT JOIN FETCH p.lastResult LEFT JOIN FETCH p.lastRatedResult
            """)
    List<ParticipantScore> findAllEagerly();

    @Query("""
            SELECT AVG(p.lastRatedScore)
            FROM ParticipantScore p
            WHERE p.exercise IN :exercises
            """)
    Double findAvgRatedScore(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT AVG(p.lastScore)
            FROM ParticipantScore p
            WHERE p.exercise IN :exercises
            """)
    Double findAvgScore(@Param("exercises") Set<Exercise> exercises);

    /**
     * Gets average score for each exercise
     *
     * @param exercises exercises to get the average score for
     * @return List<Map<String, Object>> with a map for every exercise containing exerciseId and the average score
     */
    @Query("""
            SELECT p.exercise.id AS exerciseId, AVG(p.lastScore) AS averageScore
            FROM ParticipantScore p
            WHERE p.exercise IN :exercises
            GROUP BY p.exercise.id
            """)
    List<Map<String, Object>> findAverageScoreForExercises(@Param("exercises") List<Exercise> exercises);

    /**
     * Gets average score for a single exercise
     *
     * @param exerciseId the id of the exercise to get the average score for
     * @return The average score as double
     */
    @Query("""
            SELECT AVG(p.lastScore)
            FROM ParticipantScore p
            WHERE p.exercise.id = :exerciseId
            """)
    Double findAverageScoreForExercise(@Param("exerciseId") Long exerciseId);

    /**
     * Delete all participant scores for a given exercise.
     * Note: Only call this method when the exercise is about to be deleted. Otherwise, use {@link #clearAllByResultId(Long)}.
     * @param exerciseId the exercise id for which to remove all participant scores
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW) // ok because of delete
    default void deleteAllByExerciseIdTransactional(Long exerciseId) {
        // When the exercise is deleted, we can safely remove all corresponding participant scores
        this.removeAllByExerciseId(exerciseId);
    }

    /**
     * Safely removes the result from all participant scores by setting it to null.
     * The scheduler will later evaluate and delete the participant score if no older result exists.
     * @see de.tum.in.www1.artemis.service.scheduled.ParticipantScoreSchedulerService
     * @param resultId the id of the result to be removed
     */
    default void clearAllByResultId(Long resultId) {
        this.clearLastResultByResultId(resultId);
        this.clearLastRatedResultByResultId(resultId);
    }

    @Query("""
            SELECT MAX(ps.lastModifiedDate) as latestModifiedDate
            FROM ParticipantScore ps
            """)
    Optional<Instant> getLatestModifiedDate();

    @Query("""
                    SELECT new de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresAggregatedInformation(p.exercise.id, AVG(p.lastRatedScore), MAX(p.lastRatedScore))
                    FROM ParticipantScore p
                    WHERE p.exercise IN :exercises
                    GROUP BY p.exercise

            """)
    List<ExerciseScoresAggregatedInformation> getAggregatedExerciseScoresInformation(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT new de.tum.in.www1.artemis.domain.statistics.ScoreDistribution(count(p.id), p.lastRatedScore)
            FROM ParticipantScore p
            WHERE p.exercise.id = :exerciseId
            group by p.id
            order by p.lastRatedScore asc
            """)
    List<ScoreDistribution> getScoreDistributionForExercise(@Param("exerciseId") Long exerciseId);

    /**
     * Sets the average for the given <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     * using the value provided in averageScoreById
     *
     * Quiz Exercises are a special case: They don't have a due date set in the database,
     * therefore it is hard to tell if they are over, so always calculate a score for them
     *
     * @param exerciseStatisticsDTO the <code>CourseManagementOverviewExerciseStatisticsDTO</code> to set the amounts for
     * @param averageScoreById the average score for each exercise indexed by exerciseId
     * @param exercise the exercise corresponding to the <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     */
    default void setAverageScoreForStatisticsDTO(CourseManagementOverviewExerciseStatisticsDTO exerciseStatisticsDTO, Map<Long, Double> averageScoreById, Exercise exercise) {
        Double averageScore;
        if (exercise instanceof QuizExercise) {
            averageScore = findAverageScoreForExercise(exercise.getId());
        }
        else {
            averageScore = averageScoreById.get(exercise.getId());
        }
        exerciseStatisticsDTO.setAverageScoreInPercent(averageScore != null ? averageScore : 0.0);
    }
}
