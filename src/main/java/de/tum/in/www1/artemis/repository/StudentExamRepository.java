package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the StudentExam entity.
 */
@Repository
public interface StudentExamRepository extends JpaRepository<StudentExam, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Optional<StudentExam> findWithExercisesById(Long id);

    @Query("""
            SELECT DISTINCT se FROM StudentExam se
            LEFT JOIN FETCH se.exercises e
                  WHERE se.testRun = FALSE
                      AND se.exam.id = :#{#examId}
                      AND se.user.id = :#{#userId}
            """)
    Optional<StudentExam> findWithExercisesByUserIdAndExamId(@Param("userId") long userId, @Param("examId") long examId);

    @Query("""
            SELECT DISTINCT se FROM StudentExam se
            LEFT JOIN FETCH se.exercises e
            LEFT JOIN FETCH e.studentParticipations sp
            LEFT JOIN FETCH sp.submissions s
            WHERE se.id = :#{#studentExamId}
            	AND se.testRun = :#{#isTestRun}
            """)
    Optional<StudentExam> findWithExercisesParticipationsSubmissionsById(@Param("studentExamId") Long studentExamId, @Param("isTestRun") boolean isTestRun);

    @Query("""
            SELECT se FROM StudentExam se
            WHERE se.exam.id = :#{#examId}
            	AND se.testRun = FALSE
            """)
    Set<StudentExam> findByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT se FROM StudentExam se
            LEFT JOIN FETCH se.exercises e
            WHERE se.exam.id = :#{#examId}
            	AND se.testRun = FALSE
            """)
    Set<StudentExam> findAllWithExercisesByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT se FROM StudentExam se
            WHERE se.exam.id = :#{#examId}
            	AND se.testRun = TRUE
            """)
    List<StudentExam> findAllTestRunsByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT count(se) FROM StudentExam se
            WHERE se.exam.id = :#{#examId}
            	AND se.testRun = TRUE
            """)
    long countTestRunsByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT count(se) FROM StudentExam se
            WHERE se.exam.id = :#{#examId}
            	AND se.started = TRUE
            	AND se.testRun = FALSE
            """)
    long countStudentExamsStartedByExamIdIgnoreTestRuns(@Param("examId") Long examId);

    @Query("""
            SELECT count(se) FROM StudentExam se
            WHERE se.exam.id = :#{#examId}
            	AND se.submitted = TRUE
            	AND se.testRun = FALSE
            """)
    long countStudentExamsSubmittedByExamIdIgnoreTestRuns(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se FROM StudentExam se
            LEFT JOIN FETCH se.exercises e
            LEFT JOIN FETCH e.studentParticipations sp
            LEFT JOIN FETCH sp.submissions s
            LEFT JOIN FETCH s.results r
            LEFT JOIN FETCH r.assessor a
            WHERE se.exam.id = :#{#examId}
            	AND se.testRun = TRUE
            	AND se.user.id = sp.student.id
            """)
    List<StudentExam> findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se FROM StudentExam se
            LEFT JOIN FETCH se.exercises e
            WHERE se.exam.id = :#{#examId}
            	AND se.testRun = TRUE
            	AND se.user.id = :#{#userId}
            """)
    List<StudentExam> findAllTestRunsWithExercisesByExamIdForUser(@Param("examId") Long examId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT se FROM StudentExam se
            WHERE se.testRun = FALSE
            	AND se.exam.id = :#{#examId}
            	AND se.user.id = :#{#userId}
            """)
    Optional<StudentExam> findByExamIdAndUserId(@Param("examId") long examId, @Param("userId") long userId);

    @Query("""
            SELECT DISTINCT se FROM StudentExam se
            LEFT JOIN FETCH se.exercises e
            WHERE se.testRun = FALSE
            	AND e.id = :#{#exerciseId}
            	AND se.user.id = :#{#userId}
            """)
    Optional<StudentExam> findByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT max(se.workingTime) FROM StudentExam se
            WHERE se.testRun = FALSE
            	AND se.exam.id = :#{#examId}
            """)
    Optional<Integer> findMaxWorkingTimeByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se.workingTime FROM StudentExam se
            WHERE se.testRun = FALSE
            	AND se.exam.id = :#{#examId}
            """)
    Set<Integer> findAllDistinctWorkingTimesByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT u FROM StudentExam se
            LEFT JOIN se.user u
            WHERE se.testRun = FALSE
            	AND se.exam.id = :#{#examId}
            """)
    Set<User> findUsersWithStudentExamsForExam(@Param("examId") Long examId);

    @Query("""
            SELECT se FROM StudentExam se
            LEFT JOIN FETCH se.exercises exercises
            WHERE se.exam.id = :#{#examId}
            	AND se.submitted = FALSE
            	AND se.testRun = FALSE
            """)
    Set<StudentExam> findAllUnsubmittedWithExercisesByExamId(Long examId);

    List<StudentExam> findAllByExamId_AndTestRunIsTrue(@Param("examId") Long examId);

    @NotNull
    default StudentExam findByIdElseThrow(Long studentExamId) throws EntityNotFoundException {
        return findById(studentExamId).orElseThrow(() -> new EntityNotFoundException("Student Exam", studentExamId));
    }

    /**
     * Return the StudentExam of the participation's user, if possible
     *
     * @param exercise that is possibly part of an exam
     * @param participation the participation of the student
     * @return an optional StudentExam, which is empty if the exercise is not part of an exam or the student exam hasn't been created
     */
    default Optional<StudentExam> findStudentExam(Exercise exercise, StudentParticipation participation) {
        if (exercise.isExamExercise()) {
            var examUser = participation.getStudent().orElseThrow(() -> new EntityNotFoundException("Exam Participation with " + participation.getId() + " has no student!"));
            return findByExerciseIdAndUserId(exercise.getId(), examUser.getId());
        }
        return Optional.empty();
    }

    /**
     * Return the individual due date for the exercise of the participation's user
     * <p>
     * For exam exercises, this depends on the StudentExam's working time
     *
     * @param exercise that is possibly part of an exam
     * @param participation the participation of the student
     * @return the time from which on submissions are not allowed, for exercises that are not part of an exam, this is just the due date.
     */
    @Nullable
    default ZonedDateTime getIndividualDueDate(Exercise exercise, StudentParticipation participation) {
        if (exercise.isExamExercise()) {
            var studentExam = findStudentExam(exercise, participation).orElse(null);
            if (studentExam == null) {
                return exercise.getDueDate();
            }
            return studentExam.getExam().getStartDate().plusSeconds(studentExam.getWorkingTime());
        }
        return exercise.getDueDate();
    }
}
