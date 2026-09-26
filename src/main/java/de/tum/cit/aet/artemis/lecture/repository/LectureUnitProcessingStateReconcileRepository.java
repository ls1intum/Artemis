package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;

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
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;

/**
 * The writes the reconcile walk owns, kept apart from {@link LectureUnitProcessingStateRepository} because they
 * share a property nothing else in that repository has: the walk decides on a batch read, then hashes PDFs and
 * asks Iris for a census before writing, so its decision is always older than the write it commits. Every
 * statement here therefore carries the decision's own conditions in its WHERE clause and applies to no row that
 * has moved on since — the reconcile walk never writes a whole entity back over a row it no longer recognises.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface LectureUnitProcessingStateReconcileRepository extends ArtemisJpaRepository<LectureUnitProcessingState, Long> {

    /**
     * Requeue a unit the reconcile walk found divergent, as one atomic statement rather than a re-fetch and save.
     * The walk hashes PDFs and asks Iris for a census between reading the state batch and writing, so its decision
     * always rests on an older snapshot; committing it through a whole-entity save merged that snapshot back over
     * every column, reverting a content requeue that landed in the meantime along with the markers and fingerprints
     * describing the new content. The guard therefore re-asserts what the caller decided on — same phase, same
     * confirmed fingerprint, and no claim — so deciding and writing cannot drift apart. The callers reach this
     * only for DONE and SKIPPED rows, where a claim should never be present because completion and activation
     * clear it in the same statement that sets the phase; the claim clause is the defensive half of that
     * invariant, so a violation refuses the requeue rather than wiping a claim. Reviving a FAILED unit is guarded
     * by different facts entirely and goes through {@link #reviveFailedIfUnchanged}.
     * {@code videoSourceHash}, {@code attachmentVersion} and {@code contentFingerprint} are deliberately not
     * written: the content-change path owns them, and they are what the whole-entity save reverted. So is
     * {@code revivalCount}, which only a revival spends.
     *
     * @param id                      the processing state to requeue
     * @param expectedPhase           the phase that justified the decision, re-asserted here
     * @param observedFingerprint     the confirmed fingerprint seen at batch-read time; compared null-safely
     * @param newConfirmedFingerprint {@code null} to clear the confirmation for a forced rebuild, else {@code observedFingerprint} to keep it
     * @param forceReingest           {@code TRUE} to force the next run to rewrite the unit, {@code null} to keep the row's value
     * @param qualityPipelineVersion  the pipeline version to stamp for a quality requeue, {@code null} to keep
     * @param dispatchPriority        where the requeued unit sits in the dispatch order
     * @param now                     recorded as the new {@code lastUpdated}
     * @return 1 when the requeue was applied, 0 when the row changed since the batch read
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE, ps.startedAt = NULL, ps.claimToken = NULL,
                ps.ingestionJobToken = NULL, ps.retryEligibleAt = NULL, ps.errorKey = NULL, ps.retryCount = 0,
                ps.confirmedFingerprint = :newConfirmedFingerprint, ps.forceReingest = COALESCE(:forceReingest, ps.forceReingest),
                ps.lastQualityPipelineVersion = COALESCE(:qualityPipelineVersion, ps.lastQualityPipelineVersion),
                ps.dispatchPriority = :dispatchPriority, ps.lastUpdated = :now,
                ps.lastHeartbeatAt = NULL, ps.lockedBy = NULL, ps.currentStage = NULL, ps.stageStartedAt = NULL,
                ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL
            WHERE ps.id = :id AND ps.phase = :expectedPhase AND ps.claimToken IS NULL
            AND COALESCE(ps.confirmedFingerprint, '') = COALESCE(:observedFingerprint, '')
            """)
    int requeueForReconcileIfUnchanged(@Param("id") long id, @Param("expectedPhase") ProcessingPhase expectedPhase, @Param("observedFingerprint") String observedFingerprint,
            @Param("newConfirmedFingerprint") String newConfirmedFingerprint, @Param("forceReingest") Boolean forceReingest,
            @Param("qualityPipelineVersion") Integer qualityPipelineVersion, @Param("dispatchPriority") Integer dispatchPriority, @Param("now") ZonedDateTime now);

    /**
     * Revive a FAILED unit for another attempt, as one atomic statement guarded by the exact facts that
     * authorized the revival — not just the phase and claim {@link #requeueForReconcileIfUnchanged} pins.
     * {@link de.tum.cit.aet.artemis.lecture.service.LectureIngestionReconcileService#reconcileFailedUnit} decides
     * to revive from three fields read in the same batch: {@code errorKey} (a permanent error is never revived),
     * {@code lastUpdated} (the cooldown is measured from it), and {@code revivalCount} (the bounded budget). Between
     * that read and this write, the row can be claimed, dispatched, and fail right back to FAILED — for example
     * with a newly permanent error — clearing {@code claimToken} and landing on {@code phase = FAILED} again, the
     * exact values {@code requeueForReconcileIfUnchanged}'s guard would still match. Only by also pinning
     * {@code errorKey}, {@code lastUpdated} and {@code revivalCount} does the write see that the FAILED it is
     * looking at is not the FAILED it decided on, and refuse to erase the newer error and reset its budget.
     * <p>
     * {@code confirmedFingerprint}, {@code forceReingest} and {@code lastQualityPipelineVersion} are not written at
     * all — a revival never touches them, so unlike {@link #requeueForReconcileIfUnchanged} there is nothing on
     * those columns to protect from a stale overwrite here.
     *
     * @param id                   the processing state to revive
     * @param observedErrorKey     the error key seen at batch-read time that justified the revival; compared null-safely
     * @param observedLastUpdated  the {@code lastUpdated} seen at batch-read time that proved the cooldown had elapsed
     * @param observedRevivalCount the revival count seen at batch-read time that proved the budget was not exhausted
     * @param dispatchPriority     where the revived unit sits in the dispatch order
     * @param now                  recorded as the new {@code lastUpdated}
     * @return 1 when the revival was applied, 0 when the row changed since the batch read
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE LectureUnitProcessingState ps
            SET ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.IDLE, ps.startedAt = NULL, ps.claimToken = NULL,
                ps.ingestionJobToken = NULL, ps.retryEligibleAt = NULL, ps.errorKey = NULL, ps.retryCount = 0,
                ps.revivalCount = ps.revivalCount + 1, ps.dispatchPriority = :dispatchPriority, ps.lastUpdated = :now,
                ps.lastHeartbeatAt = NULL, ps.lockedBy = NULL, ps.currentStage = NULL, ps.stageStartedAt = NULL,
                ps.stageProgress = NULL, ps.stageTotal = NULL, ps.lastProgressAt = NULL
            WHERE ps.id = :id AND ps.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.FAILED AND ps.claimToken IS NULL
            AND COALESCE(ps.errorKey, '') = COALESCE(:observedErrorKey, '')
            AND ps.lastUpdated = :observedLastUpdated
            AND ps.revivalCount = :observedRevivalCount
            """)
    int reviveFailedIfUnchanged(@Param("id") long id, @Param("observedErrorKey") String observedErrorKey, @Param("observedLastUpdated") ZonedDateTime observedLastUpdated,
            @Param("observedRevivalCount") int observedRevivalCount, @Param("dispatchPriority") Integer dispatchPriority, @Param("now") ZonedDateTime now);
}
