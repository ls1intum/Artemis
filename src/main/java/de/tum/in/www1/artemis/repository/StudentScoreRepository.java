package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.StudentScore;

@Repository
public interface StudentScoreRepository extends JpaRepository<StudentScore, Long> {

    @Query("""
                    SELECT DISTINCT ss
                    FROM StudentScore ss
                WHERE ss.user.id = :#{#userId}
            """)
    List<StudentScore> findStudentScoreByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT ss
                    FROM StudentScore ss
                    WHERE ss.user.id = :#{#userId} AND ss.exercise.id = :#{#exerciseId}
            """)
    Optional<StudentScore> findStudentScoreByStudentAndExercise(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    Optional<StudentScore> findStudentScoreByExerciseAndUser(Exercise exercise, User user);
}
