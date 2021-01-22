package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.scores.StudentScore;

@Repository
public interface StudentScoreRepository extends JpaRepository<StudentScore, Long> {

    @Query("""
            SELECT ss
            FROM StudentScore ss
            WHERE ss.lastResult.id= :#{#resultId} OR ss.lastRatedResult.id = :#{#resultId}
            """)
    Optional<StudentScore> findStudentScoreAssociatedWithResult(@Param("resultId") Long resultId);

    @Query("""
            DELETE
            FROM StudentScore ss
            WHERE ss.exercise.id= :#{#exerciseId}
            """)
    @Modifying
    void removeAssociatedWithExercise(@Param("exerciseId") Long exerciseId);

    @Query("""
                SELECT ss
                    FROM StudentScore ss
                    WHERE ss.user.id = :#{#userId} AND ss.exercise.id = :#{#exerciseId}
            """)
    Optional<StudentScore> findStudentScoreByStudentAndExercise(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    // @Query("""
    // SELECT ss
    // FROM StudentScore ss
    // WHERE ss.lastRatedResult.id= :#{#ratedRatedResultId}
    // """) Optional<StudentScore> studentScoreConnectedToRatedResult(@Param("ratedResultId") Long ratedRatedResultId);
    //
    // @Query("""
    // SELECT ss
    // FROM StudentScore ss
    // WHERE ss.lastResult.id= :#{#resultId}
    // """) Optional<StudentScore> studentScoreConnectedToResult(@Param("resultId") Long resultId);

}
