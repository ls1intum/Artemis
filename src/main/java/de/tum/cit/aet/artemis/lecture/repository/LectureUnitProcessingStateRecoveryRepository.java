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
                ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL, ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.claimToken = :claimToken
            AND ps.ingestionJobToken IS NULL
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING)
            """)
    int requeueStrandedRunIfClaimed(@Param("id") long id, @Param("claimToken") String claimToken, @Param("videoSourceHash") String videoSourceHash,
            @Param("attachmentVersion") Integer attachmentVersion, @Param("dispatchPriority") Integer dispatchPriority, @Param("now") ZonedDateTime now);

    /**
     * Settle a claimed stranded run whose unit no longer has processable content as DONE with nothing indexed: the field
     * set of {@link LectureUnitProcessingStateRepository#settleAsNothingIndexed}, plus the run-scoped ledger an in-flight
     * row still carries, but only while the recovery's claim still holds.
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
                ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL, ps.lastUpdated = :now
            WHERE ps.id = :id
            AND ps.claimToken = :claimToken
            AND ps.ingestionJobToken IS NULL
            AND ps.phase IN (de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.TRANSCRIBING, de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.INGESTING)
            """)
    int settleStrandedRunIfClaimed(@Param("id") long id, @Param("claimToken") String claimToken, @Param("now") ZonedDateTime now);
}
