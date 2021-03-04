package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresAggregatedInformation;

@Repository
public interface ParticipantScoreRepository extends JpaRepository<ParticipantScore, Long> {

    void removeAllByExercise(Exercise exercise);

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
    Long findAvgRatedScore(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT AVG(p.lastScore)
            FROM ParticipantScore p
            WHERE p.exercise IN :exercises
            """)
    Long findAvgScore(@Param("exercises") Set<Exercise> exercises);

    @Query("""
                    SELECT new de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresAggregatedInformation(p.exercise.id, AVG(p.lastRatedScore), MAX(p.lastRatedScore))
                    FROM ParticipantScore p
                    WHERE p.exercise IN :exercises
                    GROUP BY p.exercise

            """)
    List<ExerciseScoresAggregatedInformation> getAggregatedExerciseScoresInformation(@Param("exercises") Set<Exercise> exercises);

}
