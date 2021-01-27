package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.scores.TeamScore;

@Repository
public interface TeamScoreRepository extends JpaRepository<TeamScore, Long> {

    @Query("""
                    SELECT DISTINCT ts
                    FROM TeamScore ts
                WHERE ts.team.id = :#{#teamId}
            """)
    List<TeamScore> findTeamScoreByTeamId(@Param("teamId") Long teamId);

    @Query("""
                SELECT ts
                FROM TeamScore ts
                WHERE ts.team.id = :#{#teamId} AND ts.exercise.id = :#{#exerciseId}
            """)
    Optional<TeamScore> findTeamScoreByTeamAndExercise(@Param("exerciseId") Long exerciseId, @Param("teamId") Long teamId);

    Optional<TeamScore> findTeamScoreByExerciseAndTeam(Exercise exercise, Team team);
}
