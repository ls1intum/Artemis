package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.scores.TeamScore;

@Repository
public interface TeamScoreRepository extends JpaRepository<TeamScore, Long> {

    void deleteAllByTeam(Team team);

    @EntityGraph(type = LOAD, attributePaths = { "team", "exercise", "lastResult", "lastRatedResult" })
    Optional<TeamScore> findTeamScoreByExerciseAndTeam(Exercise exercise, Team team);

    @EntityGraph(type = LOAD, attributePaths = { "team", "exercise", "lastResult", "lastRatedResult" })
    List<TeamScore> findAllByExerciseIn(Set<Exercise> exercises, Pageable pageable);
}
