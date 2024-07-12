package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.dto.score.TeamScoreSum;

@Profile(PROFILE_CORE)
@Repository
public interface TeamScoreRepository extends ArtemisJpaRepository<TeamScore, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByTeamId(long teamId);

    @EntityGraph(type = LOAD, attributePaths = { "team.students", "exercise" })
    Optional<TeamScore> findByExercise_IdAndTeam_Id(Long exerciseId, Long teamId);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.score.TeamScoreSum(t.id, COALESCE(SUM(s.lastRatedPoints), 0))
            FROM TeamScore s
                LEFT JOIN s.team t
            WHERE s.exercise IN :exercises
            GROUP BY t.id
            """)
    Set<TeamScoreSum> getAchievedPointsOfTeams(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT s
            FROM TeamScore s
                LEFT JOIN FETCH s.exercise
                LEFT JOIN FETCH s.team t
                LEFT JOIN FETCH t.students
            WHERE s.exercise IN :exercises
                AND :user MEMBER OF t.students
            """)
    List<TeamScore> findAllByExerciseAndUserWithEagerExercise(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Query("""
            SELECT s
            FROM TeamScore s
                LEFT JOIN FETCH s.team t
                LEFT JOIN FETCH t.students
            WHERE s.exercise IN :exercises
                AND :user MEMBER OF t.students
            """)
    List<TeamScore> findAllByExercisesAndUser(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Transactional // ok because of delete
    @Modifying
    void deleteByExerciseAndTeam(Exercise exercise, Team team);
}
