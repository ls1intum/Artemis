package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;

@Repository
public interface ParticipantScoreRepository extends JpaRepository<ParticipantScore, Long> {

    List<ParticipantScore> removeAllByExerciseId(Long exerciseId);

    List<ParticipantScore> removeAllByLastResultId(Long lastResultId);

    List<ParticipantScore> removeAllByLastRatedResultId(Long lastResultId);

    @EntityGraph(type = LOAD, attributePaths = { "exercise", "lastResult", "lastRatedResult" })
    Optional<ParticipantScore> findParticipantScoreByLastRatedResult(Result result);

    @EntityGraph(type = LOAD, attributePaths = { "exercise", "lastResult", "lastRatedResult" })
    Optional<ParticipantScore> findParticipantScoresByLastResult(Result result);

    @NotNull
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
     * @param exerciseIds exerciseIds from all exercises to get the average score for
     * @return List<Map<String, Object>> with a map for every exercise containing exerciseId and the average Score
     */
    @Query("""
            SELECT p.exercise.id AS exerciseId, AVG(p.lastScore) AS averageScore
            FROM ParticipantScore p
            WHERE p.exercise.id IN :exerciseIds
            GROUP BY p.exercise.id
            """)
    List<Map<String, Object>> findAvgScoreForExercises(@Param("exerciseIds") List<Long> exerciseIds);

    @Transactional(propagation = Propagation.REQUIRES_NEW) // ok because of delete
    default void deleteAllByExerciseIdTransactional(Long exerciseId) {
        this.removeAllByExerciseId(exerciseId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW) // ok because of delete
    default void deleteAllByResultIdTransactional(Long resultId) {
        this.removeAllByLastResultId(resultId);
        this.removeAllByLastRatedResultId(resultId);
    }
}
