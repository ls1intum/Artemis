package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;

/**
 * Spring Data JPA repository that creates correction-round results atomically for tutor assessments and automatic feedback on empty or unsubmitted exams.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ManualResultCreationRepository extends ArtemisJpaRepository<Result, Long> {

    /**
     * Takes the row lock of the submission, so that tutors who create a manual result for it queue up instead of creating one each.
     * Only meaningful inside the transaction of {@link #saveManualResultIfCorrectionRoundIsFree}.
     *
     * @param submissionId the id of the submission to lock
     * @return the id of the locked submission, empty if it does not exist
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s.id FROM Submission s WHERE s.id = :submissionId")
    Optional<Long> lockSubmissionForManualResultCreation(@Param("submissionId") long submissionId);

    /**
     * Checks if a result of the given submission holds the given correction round, with the definition of {@link Submission#getManualResults()}: every result that is
     * neither automatic nor Athena-based counts, including one whose assessment type is not set yet: an empty result is stored before its type is set.
     *
     * @param submissionId    the id of the submission to check
     * @param correctionRound the correction round to check
     * @return true if a result that is not automatic holds the correction round
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM Result r
            WHERE r.submission.id = :submissionId
                AND r.correctionRound = :correctionRound
                AND (r.assessmentType IS NULL
                    OR r.assessmentType NOT IN (de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC,
                        de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC_ATHENA))
            """)
    boolean existsNonAutomaticResultForCorrectionRound(@Param("submissionId") long submissionId, @Param("correctionRound") int correctionRound);

    /**
     * Saves a new manual result of a correction round unless the submission already has a manual result of that round, also when another tutor creates one at the very
     * same time.
     * <p>
     * Tutors who open the same submission at the same time queue up on the lock of the submission. The first one saves its result and commits, the others then check in a
     * new snapshot that contains it and save nothing. The lock is taken first, so on MySQL (REPEATABLE READ) the snapshot of the check is only established once the lock is
     * held. It is held only for the check and the insert, which happen once per submission and correction round.
     *
     * @param submissionId    the id of the submission the result belongs to
     * @param correctionRound the correction round of the result
     * @param newResult       the result to save, not persisted yet
     * @return the saved result, empty if a result of the submission that is not automatic already holds the correction round
     */
    @Transactional // ok because the lock only makes the check and the insert atomic if both are one transaction
    default Optional<Result> saveManualResultIfCorrectionRoundIsFree(long submissionId, int correctionRound, Result newResult) {
        lockSubmissionForManualResultCreation(submissionId);
        if (existsNonAutomaticResultForCorrectionRound(submissionId, correctionRound)) {
            return Optional.empty();
        }
        return Optional.of(save(newResult));
    }

    /**
     * Saves the exam result with its feedback if the correction round is free and marks the submission as submitted for the assessment dashboard.
     *
     * @param submissionId    the id of the submission to assess
     * @param correctionRound the correction round of the result
     * @param newResult       the result with its feedback, not persisted yet
     * @return the saved result, empty if a result already holds the correction round
     */
    @Transactional
    default Optional<Result> saveExamResultIfCorrectionRoundIsFree(long submissionId, int correctionRound, Result newResult) {
        var savedResult = saveManualResultIfCorrectionRoundIsFree(submissionId, correctionRound, newResult);
        if (savedResult.isPresent()) {
            markExamSubmissionSubmitted(submissionId);
        }
        return savedResult;
    }

    /**
     * Marks the submission as submitted without merging a potentially stale collection of results.
     *
     * @param submissionId the id of the submission to mark as submitted
     */
    @Modifying
    @Query("UPDATE Submission s SET s.submitted = TRUE WHERE s.id = :submissionId AND (s.submitted = FALSE OR s.submitted IS NULL)")
    void markExamSubmissionSubmitted(@Param("submissionId") long submissionId);
}
