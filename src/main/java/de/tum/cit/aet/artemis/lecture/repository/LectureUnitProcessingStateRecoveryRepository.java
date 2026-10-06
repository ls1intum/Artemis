package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;

/**
 * The statements that recover an interrupted content change, kept apart from {@link LectureUnitProcessingStateRepository}
 * for the same reason as {@link LectureUnitProcessingStateReconcileRepository}: the recovery decides on a batch read and
 * then calls Iris before it writes, so its decision is always older than the write that commits it.
 * <p>
 * A content change commits {@link LectureUnitProcessingStateRepository#invalidateTokenIfMatches} on its own, before the
 * cleanup and the requeue that take the row out of flight. If either fails or the node stops in between, the row is left
 * in flight with no token, which no token-matching recovery can select. The recovery owns such a row through a claim:
 * {@link #claimStrandedRun} records it, and every later write pins it, so a concurrent requeue of the unit (which clears
 * the claim) turns the rest of the recovery into no-ops instead of being overwritten by it.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface LectureUnitProcessingStateRecoveryRepository extends ArtemisJpaRepository<LectureUnitProcessingState, Long> {

    /**
     * Find in-flight runs whose job token is gone and that have not been touched since the cutoff: content changes that
     * were interrupted, rather than ones still cleaning up, or ones a recovery claimed recently.
     *
     * @param cutoff rows last touched before this are considered interrupted
     * @return the stranded runs
     */
    @Query("""
            SELECT ps FROM LectureUnitProcessingState ps
            WHERE ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING)
            AND ps.ingestionJobToken IS NULL
            AND ps.lastUpdated < :cutoff
            """)
    List<LectureUnitProcessingState> findStrandedRuns(@Param("cutoff") ZonedDateTime cutoff);

    /**
     * Claim a stranded run for recovery, atomically re-checking the predicate it was selected by, so only a row that is
     * stranded at this instant is claimed. Refreshing {@code lastUpdated} hides it from the next pass, and from the stuck
     * detector, until the cutoff passes again; a recovery that dies after claiming therefore leaves a row that is selected
     * and claimed afresh later, and a second recovery that read the same batch matches nothing.
     *
     * @param id         the processing state to claim
     * @param claimToken a fresh identity for this recovery
     * @param cutoff     the cutoff the batch read used
     * @param now        recorded as the new {@code lastUpdated}
     * @return 1 when claimed, 0 when the row is no longer stranded
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.claimToken = :claimToken, ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING)
            AND ps.ingestionJobToken IS NULL
            AND ps.lastUpdated < :cutoff
            """)
    int claimStrandedRun(@Param("id") long id, @Param("claimToken") String claimToken, @Param("cutoff") ZonedDateTime cutoff, @Param("now") ZonedDateTime now);

    /**
     * Claim a unit whose processable content was removed, so its Iris content and recorded markers can be cleaned up through the same
     * claim-pinned writes as a stranded run. Covers a row in flight whose token the removal just invalidated, and a row at rest (DONE,
     * SKIPPED, or FAILED without a scheduled retry) that still records content markers because an earlier cleanup failed. A claim left
     * by a cleanup that failed or died is taken over once it is older than the cutoff; on these rows no other writer sets a claim,
     * because dispatch claims need IDLE and retry claims need a scheduled retry.
     *
     * @param id         the processing state to claim
     * @param claimToken a fresh identity for this cleanup
     * @param cutoff     a claim older than this is considered abandoned
     * @param now        recorded as the new {@code lastUpdated}
     * @return 1 when claimed, 0 when the row is not eligible or another cleanup holds a recent claim
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.claimToken = :claimToken, ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.ingestionJobToken IS NULL
            AND (ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING,
                    de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.DONE, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.SKIPPED)
                OR (ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.FAILED AND ps.retryEligibleAt IS NULL))
            AND ((ps.videoSourceHash IS NOT NULL AND ps.videoSourceHash <> '') OR ps.attachmentVersion IS NOT NULL)
            AND (ps.claimToken IS NULL OR ps.lastUpdated < :cutoff)
            """)
    int claimForContentRemoval(@Param("id") long id, @Param("claimToken") String claimToken, @Param("cutoff") ZonedDateTime cutoff, @Param("now") ZonedDateTime now);

    /**
     * Requeue a claimed stranded run for its current content: the field set of
     * {@link LectureUnitProcessingStateRepository#requeueForContentChange}, but only while the recovery's claim still holds.
     *
     * @param id                the processing state to requeue
     * @param claimToken        the recovery's claim
     * @param videoSourceHash   the current video marker, or {@code null} when the unit has no video
     * @param attachmentVersion the current attachment marker, or {@code null} when the unit has no PDF
     * @param dispatchPriority  where the requeued unit sits in the dispatch order
     * @param now               recorded as the new {@code lastUpdated}
     * @return 1 when requeued, 0 when the claim no longer holds
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE, ps.startedAt = NULL, ps.claimToken = NULL,
                ps.ingestionJobToken = NULL, ps.retryEligibleAt = NULL, ps.errorKey = NULL, ps.retryCount = 0,
                ps.contentFingerprint = NULL, ps.confirmedFingerprint = NULL,
                ps.videoSourceHash = :videoSourceHash, ps.attachmentVersion = :attachmentVersion, ps.dispatchPriority = :dispatchPriority,
                ps.lastHeartbeatAt = NULL, ps.lockedBy = NULL, ps.currentStage = NULL, ps.stageStartedAt = NULL,
                ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL, ps.lastUpdated = :now, ps.unsettledAttempts = 0
            WHERE ps.id = :id
            AND ps.claimToken = :claimToken
            AND ps.ingestionJobToken IS NULL
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING,
                de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.DONE, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.FAILED,
                de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.SKIPPED)
            """)
    int requeueStrandedRunIfClaimed(@Param("id") long id, @Param("claimToken") String claimToken, @Param("videoSourceHash") String videoSourceHash,
            @Param("attachmentVersion") Integer attachmentVersion, @Param("dispatchPriority") Integer dispatchPriority, @Param("now") ZonedDateTime now);

    /**
     * Settle a claimed row whose unit no longer has processable content as DONE with nothing indexed: the content markers
     * and fingerprints go, together with the run-scoped fields an in-flight row still carries, but only while the claim taken
     * by {@link #claimStrandedRun} or {@link #claimForContentRemoval} still holds.
     *
     * @param id         the processing state to settle
     * @param claimToken the recovery's claim
     * @param now        recorded as the new {@code lastUpdated}
     * @return 1 when settled, 0 when the claim no longer holds
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.DONE, ps.startedAt = NULL, ps.claimToken = NULL,
                ps.ingestionJobToken = NULL, ps.retryEligibleAt = NULL, ps.errorKey = NULL, ps.retryCount = 0,
                ps.videoSourceHash = NULL, ps.attachmentVersion = NULL, ps.contentFingerprint = NULL, ps.confirmedFingerprint = NULL,
                ps.lastHeartbeatAt = NULL, ps.lockedBy = NULL, ps.currentStage = NULL, ps.stageStartedAt = NULL,
                ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL, ps.lastUpdated = :now, ps.unsettledAttempts = 0
            WHERE ps.id = :id
            AND ps.claimToken = :claimToken
            AND ps.ingestionJobToken IS NULL
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING,
                de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.DONE, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.FAILED,
                de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.SKIPPED)
            """)
    int settleStrandedRunIfClaimed(@Param("id") long id, @Param("claimToken") String claimToken, @Param("now") ZonedDateTime now);

    /**
     * Requeue the run that took a unit over while a recovery was cleaning it up, for a recovery whose own requeue or settle
     * found its claim gone. The recovery's Iris deletion is not tied to the claim, so it may have removed content that run
     * already wrote. An edit relies on the same rule: whoever deletes last also requeues last, so a run dispatched after the
     * deletion rebuilds whatever it removed.
     * <p>
     * Only a run the deletion can have reached is requeued: one that is DONE, in flight with a token, or claimed for dispatch.
     * An unclaimed IDLE row dispatches after the deletion anyway; a row an edit settled because the unit has no content left
     * carries no content markers; a row still under this recovery's own claim, or claimed by a newer recovery that runs its
     * own cleanup, is left alone. The content markers are kept, since the edit that took over recorded the current ones.
     * <p>
     * The run is matched by its unit rather than by the state the recovery claimed: a manual retry deletes that state and
     * saves a replacement, whose run the deletion reaches just the same.
     *
     * @param lectureUnitId    the unit whose interrupted content change the recovery worked on
     * @param claimToken       the recovery's lost claim
     * @param dispatchPriority where the requeued unit sits in the dispatch order
     * @param now              recorded as the new {@code lastUpdated}
     * @return 1 when a run was requeued, 0 when none was exposed to the deletion
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE, ps.startedAt = NULL, ps.claimToken = NULL,
                ps.ingestionJobToken = NULL, ps.retryEligibleAt = NULL, ps.errorKey = NULL,
                ps.contentFingerprint = NULL, ps.confirmedFingerprint = NULL, ps.dispatchPriority = :dispatchPriority,
                ps.lastHeartbeatAt = NULL, ps.lockedBy = NULL, ps.currentStage = NULL, ps.stageStartedAt = NULL,
                ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL, ps.lastUpdated = :now
            WHERE ps.lectureUnit.id = :lectureUnitId
            AND (ps.claimToken IS NULL OR ps.claimToken <> :claimToken)
            AND ((ps.videoSourceHash IS NOT NULL AND ps.videoSourceHash <> '') OR ps.attachmentVersion IS NOT NULL)
            AND (ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.DONE
                OR ps.ingestionJobToken IS NOT NULL
                OR (ps.claimToken IS NOT NULL
                    AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.FAILED)))
            """)
    int requeueRunExposedToRecoveryCleanup(@Param("lectureUnitId") long lectureUnitId, @Param("claimToken") String claimToken, @Param("dispatchPriority") Integer dispatchPriority,
            @Param("now") ZonedDateTime now);
}
