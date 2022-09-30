package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.statistics.ScoreDistribution;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementOverviewExerciseStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresAggregatedInformation;

@Repository
public interface ParticipantScoreRepository extends JpaRepository<ParticipantScore, Long> {

    /**
     * Find all outdated participant scores where the last result was deleted (and therefore set to null).
     * Note: There are valid scores where the last *rated* result is null because of practice runs, see {@link #clearAllByResultId(Long)}
     * @return A list of outdated participant scores
     */
    @Query("""
            SELECT p FROM ParticipantScore p
            WHERE p.lastResult IS NULL
            """)
    List<ParticipantScore> findAllOutdated();

    @NotNull
    @Override
    @EntityGraph(type = LOAD, attributePaths = { "exercise", "lastResult", "lastRatedResult" })
    List<ParticipantScore> findAll();

    List<ParticipantScore> findAllByExercise(Exercise exercise);

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
            GROUP BY p.id
            ORDER BY p.lastRatedScore ASC
            """)
    List<ScoreDistribution> getScoreDistributionForExercise(@Param("exerciseId") Long exerciseId);

    /**
     * Delete all participant scores for a given exercise
     * Note: Only call this method when the exercise is about to be deleted. Otherwise, use {@link #clearAllByResultId(Long)}.
     * @param exerciseId the exercise id for which to remove all participant scores
     */
    @Transactional
    @Modifying
    void deleteAllByExerciseId(long exerciseId);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ParticipantScore p
            SET p.lastResult = NULL, p.lastPoints = NULL, p.lastScore = NULL
            WHERE p.lastResult.id = :lastResultId
            """)
    // Do not update last modified date
    void clearLastResultByResultId(@Param("lastResultId") Long lastResultId);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ParticipantScore p
            SET p.lastRatedResult = NULL, p.lastRatedPoints = NULL, p.lastRatedScore = NULL
            WHERE p.lastRatedResult.id = :lastResultId
            """)
    // Do not update last modified date
    void clearLastRatedResultByResultId(@Param("lastResultId") Long lastResultId);

    /**
     * Sets the average for the given <code>CourseManagementOverviewExerciseStatisticsDTO</code>
     * using the value provided in averageScoreById
     * <p>
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
