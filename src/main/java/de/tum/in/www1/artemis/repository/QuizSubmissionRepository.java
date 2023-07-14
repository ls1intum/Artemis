package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;

/**
 * Spring Data JPA repository for the QuizSubmission entity.
 */
@Repository
public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    @Query("""
            SELECT DISTINCT submission FROM QuizSubmission submission
            LEFT JOIN FETCH submission.submittedAnswers
            LEFT JOIN FETCH submission.results r
            LEFT JOIN FETCH r.feedbacks
            LEFT JOIN FETCH r.assessor
            WHERE submission.id = :#{#submissionId}
            """)
    Optional<QuizSubmission> findWithEagerResultAndFeedbackById(@Param("submissionId") long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "submittedAnswers" })
    QuizSubmission findWithEagerSubmittedAnswersById(@Param("submissionId") long submissionId);

    Set<QuizSubmission> findByParticipation_Exercise_Id(long exerciseId);

    @Query("""
            SELECT submission FROM QuizSubmission submission
            JOIN submission.participation participation
            JOIN participation.exercise exercise
            WHERE exercise.id = :#{#quizExerciseId}
            """)
    Optional<QuizSubmission> findByQuizExerciseId(@Param("quizExerciseId") long quizExerciseId);

    /**
     * Retrieve QuizSubmission for given quiz batch and studentLogin
     *
     * @param quizBatch    the quiz batch for which QuizSubmission is to be retrieved
     * @param studentLogin the login of the student for which QuizSubmission is to be retrieved
     * @return QuizSubmission for given quiz batch and studentLogin
     */
    @Query("""
            SELECT submission
            FROM QuizSubmission submission
                JOIN QuizBatch quizBatch ON submission.quizBatch = quizBatch.id
                JOIN TREAT(submission.participation AS StudentParticipation) participation
            WHERE quizBatch.id = :#{#quizBatch.id}
                AND participation.student.login = :#{#studentLogin}
            """)
    Set<QuizSubmission> findAllByQuizBatchAndStudentLogin(@Param("quizBatch") QuizBatch quizBatch, @Param("studentLogin") String studentLogin);
}
