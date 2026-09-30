package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.exception.QuizSubmissionException;

/**
 * Spring Data JPA repository for the QuizSubmission entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizSubmissionRepository extends ArtemisJpaRepository<QuizSubmission, Long> {

    /**
     * Take a write lock on the submission row, so that the requests which replace its answers run one after the other.
     * The lock is held until the surrounding transaction ends, see {@link #replaceAnswersOfUnsubmittedSubmission}.
     *
     * @param submissionId the id of the submission to lock
     * @return the locked submission, if it exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT submission
            FROM QuizSubmission submission
            WHERE submission.id = :submissionId
            """)
    Optional<QuizSubmission> findByIdWithWriteLock(@Param("submissionId") long submissionId);

    /**
     * Replace the answers of the submission that is saved or submitted in live mode, one request after the other per
     * submission. A client saves the answers while the student works and submits them at the end, and the two requests
     * can reach the server at the same time. Each of them replaces the answers, and two replacements that run together
     * both insert theirs: the submission ends up with two answers to a question, and which of them is scored depends on
     * the order the database returns them in. Whoever takes the lock last wins, and a save that waited for the submit is
     * rejected, as the quiz is submitted by then.
     *
     * @param submission the submission with the new answers, carrying the id of the stored submission
     * @return the stored submission
     * @throws QuizSubmissionException if the stored submission has been submitted already
     */
    @Transactional // ok: the lock, the check and the replacement of the answers are only correct as one unit
    default QuizSubmission replaceAnswersOfUnsubmittedSubmission(QuizSubmission submission) throws QuizSubmissionException {
        // The state is read after the lock is taken: a request that waited for another one sees what it stored
        var stored = getValueElseThrow(findByIdWithWriteLock(submission.getId()), submission.getId());
        if (stored.isSubmitted()) {
            throw new QuizSubmissionException("You have already submitted the quiz");
        }
        return save(submission);
    }

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
     * Check whether the submission with the given id belongs to the given participation. The exam quiz save uses it before a
     * client-supplied submission id drives a merge, so the id cannot name a row of another participation.
     *
     * @param submissionId    the id of the submission to validate
     * @param participationId the participation the submission must belong to
     * @return {@code true} if a submission with that id exists in the given participation
     */
    boolean existsByIdAndParticipationId(long submissionId, long participationId);
}
