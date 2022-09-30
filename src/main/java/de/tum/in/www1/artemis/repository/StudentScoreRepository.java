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
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO;

@Repository
public interface StudentScoreRepository extends JpaRepository<StudentScore, Long> {

    void deleteAllByUser(User user);

    @EntityGraph(type = LOAD, attributePaths = { "user", "exercise" })
    Optional<StudentScore> findByExercise_IdAndUser_Id(Long exerciseId, Long userId);

    @EntityGraph(type = LOAD, attributePaths = { "user", "exercise", "lastResult", "lastRatedResult" })
    List<StudentScore> findAllByExerciseIn(Set<Exercise> exercises, Pageable pageable);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO(s.user.login, AVG(s.lastScore), AVG(s.lastRatedScore), AVG(s.lastPoints), AVG(s.lastRatedPoints))
            FROM StudentScore s
            WHERE s.exercise IN :exercises
            GROUP BY s.user
            """)
    List<ParticipantScoreAverageDTO> getAvgScoreOfStudentsInExercises(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT DISTINCT s
            FROM StudentScore s
            WHERE s.exercise = :exercise AND s.user = :user
            """)
    Optional<StudentScore> findStudentScoreByExerciseAndUserLazy(@Param("exercise") Exercise exercise, @Param("user") User user);

    // TODO: use a custom object instead of Object[] (as in the example above with ParticipantScoreAverageDTO)
    @Query("""
            SELECT sc.user, SUM(sc.lastRatedPoints)
            FROM StudentScore sc
            WHERE sc.exercise IN :exercises
            GROUP BY sc.user
            """)
    List<Object[]> getAchievedPointsOfStudents(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT s
            FROM StudentScore s LEFT JOIN FETCH s.exercise
            WHERE s.exercise IN :exercises AND s.user = :user
            """)
    List<StudentScore> findAllByExerciseAndUserWithEagerExercise(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Transactional
    @Modifying
    void deleteByExerciseAndUser(Exercise exercise, User user);
}
