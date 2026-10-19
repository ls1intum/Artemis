package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseAverageScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.ScoreDistributionDTO;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseScoresAggregatedInformation;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ParticipantScoreRepository extends ArtemisJpaRepository<ParticipantScore, Long> {

    /**
     * Find all outdated participant scores that need to be recomputed by the scheduled service.
     * <p>
     * A score is outdated when:
     * <ul>
     * <li>its last result was deleted (and therefore set to null), or</li>
     * <li>its last <i>rated</i> result reference was set to null while the rated score/points were left behind. This can
     * only happen through the database-level {@code ON DELETE SET NULL} on {@code last_rated_result_id} (which nulls the
     * reference but not the derived numeric columns) when a result is deleted concurrently with an async score update;
     * a consistent score always has the rated reference and the rated score/points either both set or both null, so a
     * non-null {@code lastRatedScore} with a null {@code lastRatedResult} is exactly this stale state. Matching it here
     * (rather than only on {@code lastResult IS NULL}) ensures such a score is repaired, because when the rated result
     * is nulled but a different last result survives, the score would otherwise never be picked up again.</li>
     * </ul>
     * A validly null rated result (e.g. a practice run, where {@code lastRatedScore} is also null, see
     * {@link #clearAllByResultId(Long)}) is intentionally not matched.
     *
     * @return A list of outdated participant scores
     */
    @Query("""
            SELECT p
            FROM ParticipantScore p
            WHERE p.lastResult IS NULL
                OR (p.lastRatedResult IS NULL AND p.lastRatedScore IS NOT NULL)
            """)
    List<ParticipantScore> findAllOutdated();

    @EntityGraph(type = LOAD, attributePaths = { "exercise", "lastResult", "lastRatedResult" })
    List<ParticipantScore> findAllByExercise(Exercise exercise);

    /**
     * Gets average rated score for a set of exercise
     *
     * @param exercises The set of exercises to get the average rated score for
     * @return The average rated score as double
     */
    @Query("""
            SELECT AVG(p.lastRatedScore)
            FROM ParticipantScore p
            WHERE p.exercise IN :exercises
            """)
    Double findAvgRatedScore(@Param("exercises") Set<Exercise> exercises);

    /**
     * Gets average score for each exercise
     *
     * @param exercises exercises to get the average score for
     * @return list of exerciseId and corresponding average score
     */
    @Query("""
                SELECT new de.tum.cit.aet.artemis.assessment.dto.ExerciseAverageScoreDTO(p.exercise.id, AVG(p.lastScore))
                FROM ParticipantScore p
                WHERE p.exercise IN :exercises
                GROUP BY p.exercise.id
            """)
    List<ExerciseAverageScoreDTO> findAverageScoreForExercises(@Param("exercises") Collection<Exercise> exercises);

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
     *
     * @param resultId the id of the result to be removed
     * @see ParticipantScoreScheduleService
     */
    @Transactional // ok because of delete
    default void clearAllByResultId(Long resultId) {
        this.clearLastResultByResultId(resultId);
        this.clearLastRatedResultByResultId(resultId);
    }

    @Query("""
            SELECT MAX(ps.lastModifiedDate) AS latestModifiedDate
            FROM ParticipantScore ps
            """)
    Optional<Instant> getLatestModifiedDate();

    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.ExerciseScoresAggregatedInformation(
                p.exercise.id,
                AVG(p.lastRatedScore),
                MAX(p.lastRatedScore)
            )
            FROM ParticipantScore p
            WHERE p.exercise IN :exercises
            GROUP BY p.exercise.id
            """)
    List<ExerciseScoresAggregatedInformation> getAggregatedExerciseScoresInformation(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.ScoreDistributionDTO(count(p.id), p.lastRatedScore)
            FROM ParticipantScore p
            WHERE p.exercise.id = :exerciseId
            GROUP BY p.id
            ORDER BY p.lastRatedScore ASC
            """)
    List<ScoreDistributionDTO> getScoreDistributionForExercise(@Param("exerciseId") Long exerciseId);

    /**
     * Delete all participant scores for a given exercise
     * Note: Only call this method when the exercise is about to be deleted. Otherwise, use {@link #clearAllByResultId(Long)}.
     *
     * @param exerciseId the exercise id for which to remove all participant scores
     */
    @Transactional // ok because of delete
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

}
