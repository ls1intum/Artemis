package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Query("select distinct se from StudentExam se left join fetch se.exercises e where se.testRun = false and se.exam.id = :#{#examId} and se.user.id = :#{#userId}")
    Optional<StudentExam> findWithExercisesByUserIdAndExamId(@Param("userId") long userId, @Param("examId") long examId);

    @Query("select distinct se from StudentExam se left join fetch se.exercises e left join fetch e.studentParticipations sp left join fetch sp.submissions s where se.id = :#{#studentExamId} and se.testRun = :#{#isTestRun}")
    Optional<StudentExam> findWithExercisesParticipationsSubmissionsById(@Param("studentExamId") Long studentExamId, @Param("isTestRun") boolean isTestRun);

    @Query("select se from StudentExam se where se.exam.id = :#{#examId} and se.testRun = false")
    Set<StudentExam> findByExamId(@Param("examId") Long examId);

    @Query("select se from StudentExam se left join fetch se.exercises e where se.exam.id = :#{#examId} and se.testRun = false")
    Set<StudentExam> findAllWithExercisesByExamId(@Param("examId") Long examId);

    @Query("select se from StudentExam se where se.exam.id = :#{#examId} and se.testRun = true")
    List<StudentExam> findAllTestRunsByExamId(@Param("examId") Long examId);

    @Query("select count(se) from StudentExam se where se.exam.id = :#{#examId} and se.testRun = true")
    long countTestRunsByExamId(@Param("examId") Long examId);

    @Query("select count(se) from StudentExam se where se.exam.id = :#{#examId} and se.started = true and se.testRun = false")
    long countStudentExamsStartedByExamIdIgnoreTestRuns(@Param("examId") Long examId);

    @Query("select count(se) from StudentExam se where se.exam.id = :#{#examId} and se.submitted = true and se.testRun = false")
    long countStudentExamsSubmittedByExamIdIgnoreTestRuns(@Param("examId") Long examId);

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
