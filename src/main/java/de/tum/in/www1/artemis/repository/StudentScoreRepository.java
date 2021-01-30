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
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.StudentScore;

@Repository
public interface StudentScoreRepository extends JpaRepository<StudentScore, Long> {

    void deleteAllByUser(User user);

    @EntityGraph(type = LOAD, attributePaths = { "user", "exercise", "lastResult", "lastRatedResult" })
    Optional<StudentScore> findStudentScoreByExerciseAndUser(Exercise exercise, User user);

    @EntityGraph(type = LOAD, attributePaths = { "user", "exercise", "lastResult", "lastRatedResult" })
    List<StudentScore> findAllByExerciseIn(Set<Exercise> exercises, Pageable pageable);

}
