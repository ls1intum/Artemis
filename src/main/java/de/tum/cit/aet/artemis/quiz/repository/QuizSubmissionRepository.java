package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;

/**
 * Spring Data JPA repository for the QuizSubmission entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizSubmissionRepository extends ArtemisJpaRepository<QuizSubmission, Long> {

    @Query("""
            SELECT DISTINCT submission
            FROM QuizSubmission submission
                LEFT JOIN FETCH submission.submittedAnswers
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.feedbacks
                LEFT JOIN FETCH r.assessor
            WHERE submission.id = :submissionId
            """)
    Optional<QuizSubmission> findWithEagerResultAndFeedbackById(@Param("submissionId") long submissionId);

    @Query("""
                SELECT DISTINCT s
                FROM QuizSubmission s
                    LEFT JOIN FETCH s.submittedAnswers
                WHERE s.participation.id IN :participationIds
            """)
    List<QuizSubmission> findWithEagerSubmittedAnswersByParticipationIds(@Param("participationIds") Set<Long> participationIds);

    @EntityGraph(type = LOAD, attributePaths = { "submittedAnswers" })
    QuizSubmission findWithEagerSubmittedAnswersById(long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "submittedAnswers" })
    List<QuizSubmission> findWithEagerSubmittedAnswersByParticipationId(long participationId);

    @Query("""
            SELECT DISTINCT submission
            FROM QuizSubmission submission
                LEFT JOIN FETCH submission.submittedAnswers
                JOIN submission.results r
            WHERE r.id = :resultId
            """)
    Optional<QuizSubmission> findWithEagerSubmittedAnswersByResultId(@Param("resultId") long resultId);

    /**
     * Retrieve QuizSubmission for given quiz batch and studentLogin
     *
     * @param quizBatchId  the id of the quiz batch for which QuizSubmission is to be retrieved
     * @param studentLogin the login of the student for which QuizSubmission is to be retrieved
     * @return QuizSubmission for given quiz batch and studentLogin
     */
    @Query("""
            SELECT submission
            FROM QuizSubmission submission
                JOIN QuizBatch quizBatch ON submission.quizBatch = quizBatch.id
                JOIN TREAT(submission.participation AS StudentParticipation) participation
            WHERE quizBatch.id = :quizBatchId
                AND participation.student.login = :studentLogin
            """)
    Set<QuizSubmission> findAllByQuizBatchAndStudentLogin(@Param("quizBatchId") Long quizBatchId, @Param("studentLogin") String studentLogin);

    @Query("""
            SELECT submission
            FROM QuizSubmission submission
                LEFT JOIN TREAT(submission.participation AS StudentParticipation) participation
            WHERE participation.exercise.id = :exerciseId
                AND participation.student.login = :studentLogin
            """)
    Optional<QuizSubmission> findByExerciseIdAndStudentLogin(@Param("exerciseId") Long exerciseId, @Param("studentLogin") String studentLogin);

    /**
     * Check whether the submission with the given id belongs to the given student. Used by the test-exam quiz
     * submission endpoint to validate that a client-supplied submission id actually belongs to the requesting
     * user before letting it drive an UPDATE on the row (test exams skip {@code preventMultipleSubmissions},
     * so the otherwise-implicit ownership guarantee from that helper does not apply).
     *
     * @param submissionId the id of the submission to validate
     * @param studentId    the id of the student that must own the submission
     * @return {@code true} if a submission with that id exists and is owned by the given student
     */
    @Query("""
            SELECT COUNT(submission) > 0
            FROM QuizSubmission submission
                JOIN TREAT(submission.participation AS StudentParticipation) participation
            WHERE submission.id = :submissionId
                AND participation.student.id = :studentId
            """)
    boolean existsByIdAndStudentId(@Param("submissionId") Long submissionId, @Param("studentId") Long studentId);
}
