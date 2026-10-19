package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.StoredSubmittedAnswerIdDTO;
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
     * Save the answers of a submission that is saved or submitted by the student in the live mode, one request after the
     * other per submission. A client saves the answers while the student works and submits them at the end, and the two
     * requests can reach the server at the same time. The one that is saved last has to win, and a save that waited for
     * the submit is rejected, as the quiz is submitted by then and cannot be changed any more. Only the lock gives that:
     * without it, a save that was checked before the submit and is stored after it would write {@code submitted = false}
     * over the submit.
     * <p>
     * The stored answers are updated, not replaced: an answer to a question the submission already has an answer for
     * is written to that row, see {@link QuizSubmission#adoptIdsOfStoredAnswers}. The state is read after the lock is
     * taken, so a request that waited for another one sees what it stored.
     *
     * @param submission the submission with the new answers, carrying the id of the stored submission
     * @return the stored submission
     * @throws QuizSubmissionException if the stored submission has been submitted already
     */
    @Transactional // ok: the lock, the check and the replacement of the answers are only correct as one unit
    default QuizSubmission replaceAnswersOfUnsubmittedSubmission(QuizSubmission submission) throws QuizSubmissionException {
        var stored = getValueElseThrow(findByIdWithWriteLock(submission.getId()), submission.getId());
        if (stored.isSubmitted()) {
            throw new QuizSubmissionException("You have already submitted the quiz");
        }
        Map<Long, Long> storedAnswerIdByQuestionId = new HashMap<>();
        for (SubmittedAnswer storedAnswer : stored.getSubmittedAnswers()) {
            // several stored answers to one question can only come from before the unique index: the latest one is updated, the others are removed by the save
            if (storedAnswer.getQuizQuestion() != null) {
                storedAnswerIdByQuestionId.merge(storedAnswer.getQuizQuestion().getId(), storedAnswer.getId(), Math::max);
            }
        }
        submission.adoptIdsOfStoredAnswers(storedAnswerIdByQuestionId);
        return save(submission);
    }

    /**
     * The ids of the answers stored for a submission, with the id of the question each of them answers. Reads no entities,
     * so a save can give the answers it writes the ids of the rows they replace for the price of one statement.
     *
     * @param submissionId the id of the submission
     * @return the stored answers, empty if there are none
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.quiz.dto.submittedanswer.StoredSubmittedAnswerIdDTO(answer.quizQuestion.id, answer.id)
            FROM SubmittedAnswer answer
            WHERE answer.submission.id = :submissionId
            """)
    List<StoredSubmittedAnswerIdDTO> findStoredAnswerIdsBySubmissionId(@Param("submissionId") long submissionId);

    /**
     * Save the submission of an exam, writing its answers to the rows of the stored answers to the same questions.
     * <p>
     * The answers of an exam, in the autosave as well as in the final hand-in, arrive without ids. The database allows only
     * one answer per question and submission, and replacing an answer inserts the new one before the old one is removed, so
     * every answer has to take over the id of the stored answer to its question, see {@link QuizSubmission#adoptIdsOfStoredAnswers}.
     * <p>
     * This needs no lock: an exam save marks the submission as submitted every time and the student can save again, so
     * there is no stored state that a later request has to respect. Two saves that reach the server together cannot store
     * two answers to a question either, as the database allows only one. The one that would insert the second answer, or
     * that loses a deadlock over the rows the other one updates, fails and is repeated, and then updates what the first one
     * stored. A violation of another constraint is not the other save's doing and is not repeated.
     *
     * @param submission           the submission with the new answers
     * @param knownStoredAnswerIds the ids of the stored answers by the id of the question they answer, if the caller has read the stored answers already, else
     *                                 {@code null} and the ids are read here. Only the first attempt uses it, as a repeated one has to see what the other save stored.
     * @return the stored submission
     */
    default QuizSubmission saveUpdatingStoredAnswers(QuizSubmission submission, @Nullable Map<Long, Long> knownStoredAnswerIds) {
        final int maximumAttempts = 3;
        for (int attempt = 1;; attempt++) {
            Map<Long, Long> storedAnswerIdByQuestionId = new HashMap<>();
            if (attempt == 1 && knownStoredAnswerIds != null) {
                storedAnswerIdByQuestionId.putAll(knownStoredAnswerIds);
            }
            else if (submission.getId() != null) {
                // several stored answers to one question can only come from before the unique index: the latest one is updated, the others are removed by the save
                findStoredAnswerIdsBySubmissionId(submission.getId())
                        .forEach(storedAnswer -> storedAnswerIdByQuestionId.merge(storedAnswer.questionId(), storedAnswer.answerId(), Math::max));
            }
            submission.adoptIdsOfStoredAnswers(storedAnswerIdByQuestionId);
            try {
                return save(submission);
            }
            catch (DataIntegrityViolationException e) {
                if (attempt == maximumAttempts || !isUniqueConstraintViolation(e)) {
                    throw e;
                }
            }
            catch (ConcurrencyFailureException e) {
                if (attempt == maximumAttempts) {
                    throw e;
                }
            }
        }
    }

    private static boolean isUniqueConstraintViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                return true;
            }
        }
        return false;
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
