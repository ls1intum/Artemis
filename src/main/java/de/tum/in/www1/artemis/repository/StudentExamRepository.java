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

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

/**
 * Spring Data JPA repository for the StudentExam entity.
 */
@Repository
public interface StudentExamRepository extends JpaRepository<StudentExam, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Optional<StudentExam> findWithExercisesById(Long id);

    @Query("select distinct se from StudentExam se left join fetch se.exercises e where se.testRun = false and se.exam.id = :#{#examId} and se.user.id = :#{#userId} ")
    Optional<StudentExam> findWithExercisesByUserIdAndExamId(@Param("userId") long userId, @Param("examId") long examId);

    @Query("select distinct se from StudentExam se left join fetch se.exercises e left join fetch e.studentParticipations sp left join fetch sp.submissions s where se.id = :#{#studentExamId} and se.testRun = :#{#isTestRun}")
    Optional<StudentExam> findWithExercisesParticipationsSubmissionsById(@Param("studentExamId") Long studentExamId, @Param("isTestRun") boolean isTestRun);

    @Query("select se from StudentExam se where se.exam.id = :#{#examId} and se.testRun = false")
    Set<StudentExam> findByExamId(@Param("examId") Long examId);

    @Query("select se from StudentExam se left join fetch se.exercises e where se.exam.id = :#{#examId} and se.testRun = false")
    Set<StudentExam> findAllWithExercisesByExamId(@Param("examId") Long examId);

    @Query("select se from StudentExam se where se.exam.id = :#{#examId} and se.testRun = true")
    List<StudentExam> findAllTestRunsByExamId(@Param("examId") Long examId);

    @Query("select distinct se from StudentExam se left join fetch se.exercises e left join fetch e.studentParticipations sp left join fetch sp.submissions s left join fetch s.results r left join fetch r.assessor a where se.exam.id = :#{#examId} and se.testRun = true and se.user.id = sp.student.id")
    List<StudentExam> findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(@Param("examId") Long examId);

    @Query("select distinct se from StudentExam se left join fetch se.exercises e where se.exam.id = :#{#examId} and se.testRun = true and se.user.id = :#{#userId}")
    List<StudentExam> findAllTestRunsWithExercisesByExamIdForUser(@Param("examId") Long examId, @Param("userId") Long userId);

    @Query("select distinct se from StudentExam se where se.testRun = false and se.exam.id = :#{#examId} and se.user.id = :#{#userId} ")
    Optional<StudentExam> findByExamIdAndUserId(@Param("examId") long examId, @Param("userId") long userId);

    @Query("select distinct se from StudentExam se left join fetch se.exercises e where se.testRun = false and e.id = :#{#exerciseId} and se.user.id = :#{#userId}")
    Optional<StudentExam> findByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("select max(se.workingTime) from StudentExam se where se.testRun = false and se.exam.id = :#{#examId}")
    Optional<Integer> findMaxWorkingTimeByExamId(@Param("examId") Long examId);

    @Query("select distinct se.workingTime from StudentExam se where se.testRun = false and se.exam.id = :#{#examId}")
    Set<Integer> findAllDistinctWorkingTimesByExamId(@Param("examId") Long examId);

    @Query("select distinct u from StudentExam se left join se.user u where se.testRun = false and se.exam.id = :#{#examId}")
    Set<User> findUsersWithStudentExamsForExam(@Param("examId") Long examId);

    @Query("SELECT studentExam FROM StudentExam studentExam LEFT JOIN FETCH studentExam.exercises exercises WHERE studentExam.exam.id = :#{#examId} AND studentExam.submitted = FALSE AND studentExam.testRun = FALSE")
    Set<StudentExam> findAllUnsubmittedWithExercisesByExamId(Long examId);
}
