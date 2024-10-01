package de.tum.cit.aet.artemis.exam.test_repository;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;

@Repository
@Primary
public interface StudentExamTestRepository extends StudentExamRepository {

    List<StudentExam> findAllByExamId_AndTestRunIsTrue(Long examId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
                LEFT JOIN FETCH e.studentParticipations sp
                LEFT JOIN FETCH sp.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor a
            WHERE se.exam.id = :examId
            	AND se.testRun = TRUE
            	AND se.user.id = sp.student.id
            """)
    List<StudentExam> findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(@Param("examId") Long examId);

    /**
     * Get all student exams for the given exam id with exercises.
     *
     * @param ids the ids of the student exams
     * @return the list of student exams with exercises
     */
    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.id IN :ids
            """)
    List<StudentExam> findAllWithEagerExercisesById(@Param("ids") List<Long> ids);

    /**
     * Get all student exams for the given exam id with quiz questions.
     *
     * @param ids the ids of the student exams
     * @return the list of student exams with quiz questions
     */
    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.quizQuestions qq
            WHERE se.id IN :ids
            """)
    List<StudentExam> findAllWithEagerQuizQuestionsById(@Param("ids") List<Long> ids);

    // Normally, there should only be one student exam for the same user/exam pair (except test runs for instructors)
    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.testRun = FALSE
                AND se.exam.id = :examId
                AND se.user.id = :userId
            """)
    List<StudentExam> findAllWithExercisesByUserIdAndExamId(@Param("userId") long userId, @Param("examId") long examId);

    /**
     * Get the maximal working time of all student exams for the exam with the given id.
     *
     * @param examId the id of the exam
     * @return the maximum of all student exam working times for the given exam
     * @throws EntityNotFoundException if no student exams could be found
     */
    @NotNull
    default Integer findMaxWorkingTimeByExamIdElseThrow(Long examId) {
        return getArbitraryValueElseThrow(findMaxWorkingTimeByExamId(examId), Long.toString(examId));
    }
}
