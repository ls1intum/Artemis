package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO;

@Repository
public interface TeamScoreRepository extends JpaRepository<TeamScore, Long> {

    @Transactional
    @Modifying
    void deleteAllByTeamId(long teamId);

    @EntityGraph(type = LOAD, attributePaths = { "team", "exercise" })
    Optional<TeamScore> findByExercise_IdAndTeam_Id(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    @EntityGraph(type = LOAD, attributePaths = { "team", "exercise", "lastResult", "lastRatedResult" })
    List<TeamScore> findAllByExerciseIn(Set<Exercise> exercises, Pageable pageable);

    @Query("""
                    SELECT new de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO(t.team.name, AVG(t.lastScore), AVG(t.lastRatedScore), AVG(t.lastPoints), AVG(t.lastRatedPoints))
                    FROM TeamScore t
                    WHERE t.exercise IN :exercises
                    GROUP BY t.team

            """)
    List<ParticipantScoreAverageDTO> getAvgScoreOfTeamInExercises(@Param("exercises") Set<Exercise> exercises);

    @Query("""
                  SELECT DISTINCT t
                  FROM TeamScore t
                  WHERE t.exercise = :exercise AND :user MEMBER OF t.team.students
            """)
    Optional<TeamScore> findTeamScoreByExerciseAndUserLazy(@Param("exercise") Exercise exercise, @Param("user") User user);

    @Query("""
            SELECT ts.team, SUM(ts.lastRatedPoints)
            FROM TeamScore ts
            WHERE ts.exercise IN :exercises
            GROUP BY ts.team
            """)
    List<Object[]> getAchievedPointsOfTeams(@Param("exercises") Set<Exercise> exercises);

    @Query("""
                    SELECT t
                    FROM TeamScore t LEFT JOIN FETCH t.exercise
                    WHERE t.exercise IN :exercises AND :user MEMBER OF t.team.students
            """)
    List<TeamScore> findAllByExerciseAndUserWithEagerExercise(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Transactional
    @Modifying
    void deleteByExerciseAndTeam(Exercise exercise, Team team);
}
