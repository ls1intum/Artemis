package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.StudentExam;

/**
 * Spring Data JPA repository for the StudentExam entity.
 */
@Repository
public interface StudentExamRepository extends JpaRepository<StudentExam, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Optional<StudentExam> findWithExercisesById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Optional<StudentExam> findWithExercisesByUserIdAndExamId(@Param("userId") long userId, @Param("examId") long examId);

    List<StudentExam> findByExamId(Long examId);

    Optional<StudentExam> findByExamIdAndUserId(Long examId, Long userId);

    @Query("select distinct se from StudentExam se left join se.exercises e where e.id = :#{#exerciseId} and se.user.id = :#{#userId}")
    Optional<StudentExam> findByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select max(se.workingTime) from StudentExam se where se.exam.id = :#{#examId}")
    Optional<Integer> findMaxWorkingTimeByExamId(@Param("examId") Long examId);

    @Query("select distinct se.workingTime from StudentExam se where se.exam.id = :#{#examId}")
    Set<Integer> findAllDistinctWorkingTimesByExamId(@Param("examId") Long examId);
}
