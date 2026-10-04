package de.tum.cit.aet.artemis.lecture.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRecoveryRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Service that orchestrates the automated lecture content processing pipeline.
 * <p>
 * Uses a database-backed job queue pattern (PostgreSQL SKIP LOCKED) with processing phases:
 * <ul>
 * <li>IDLE: Queued, waiting for dispatch to Iris</li>
 * <li>TRANSCRIBING: Iris is generating transcription (video download → Whisper → slide alignment)</li>
 * <li>INGESTING: Iris is ingesting content into the vector database</li>
 * <li>DONE: Processing completed successfully</li>
 * <li>FAILED: Processing failed after max retries</li>
 * </ul>
 * <p>
 * Artemis sends ONE request to Iris with all available data. Iris orchestrates what processing
 * is needed (transcription, ingestion, or both) and sends checkpoint callbacks to advance the state.
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class LectureContentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LectureContentProcessingService.class);

    /**
     * Dispatch priority of user- and content-triggered work: always first in the queue.
     */
    static final int FRESH_DISPATCH_PRIORITY = 0;

    /**
     * Dispatch priority of backfill and reconcile work: only dispatched when no fresh work waits.
     */
    static final int BACKLOG_DISPATCH_PRIORITY = 2;

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final FeatureToggleService featureToggleService;

    private final ProcessingStateCallbackService processingStateCallbackService;

    private final AttachmentRepository attachmentRepository;

    private final LectureUnitProcessingStateRecoveryRepository recoveryRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    public LectureContentProcessingService(LectureUnitProcessingStateRepository processingStateRepository, Optional<IrisLectureApi> irisLectureApi,
            FeatureToggleService featureToggleService, ProcessingStateCallbackService processingStateCallbackService, AttachmentRepository attachmentRepository,
            LectureUnitProcessingStateRecoveryRepository recoveryRepository, LectureTranscriptionRepository transcriptionRepository) {
        this.processingStateRepository = processingStateRepository;
        this.irisLectureApi = irisLectureApi;
        this.featureToggleService = featureToggleService;
        this.processingStateCallbackService = processingStateCallbackService;
        this.attachmentRepository = attachmentRepository;
        this.recoveryRepository = recoveryRepository;
        this.transcriptionRepository = transcriptionRepository;
    }

    /**
     * Check if Iris is available for processing.
     * Used by the scheduler to skip backfill when Iris is not configured.
     *
     * @return true if Iris is available
     */
    public boolean hasProcessingCapabilities() {
        return irisLectureApi.isPresent();
    }

    // -------------------- Public API --------------------

    /**
     * Main entry point: Trigger processing for an AttachmentVideoUnit.
     * Called when a unit is created or updated.
     * <p>
     * Creates an IDLE processing state (enqueues the job) and then calls the dispatcher
     * to send it to Iris if capacity is available. If no slots are free, the job stays
     * queued and will be dispatched when a slot opens.
     *
     * @param unit the attachment video unit to process
     */
    @Async
    public void triggerProcessing(AttachmentVideoUnit unit) {
        SecurityUtils.setAuthorizationObject();
        doTriggerProcessing(unit, Optional.empty(), FRESH_DISPATCH_PRIORITY);
    }

    /**
     * Trigger processing with backlog priority: used by the backfill scheduler and the ingestion
     * reconciler, whose work must never starve fresh, user-triggered uploads in the dispatch queue.
     *
     * @param unit the attachment video unit to process
     */
    @Async
    public void triggerProcessingAsBacklog(AttachmentVideoUnit unit) {
        SecurityUtils.setAuthorizationObject();
        doTriggerProcessing(unit, Optional.empty(), BACKLOG_DISPATCH_PRIORITY);
    }

    /**
     * Core processing logic - enqueues a job as IDLE and triggers dispatch.
     * <p>
     * Always creates the IDLE state even when the feature toggle is OFF, so units
     * are immediately picked up when the toggle is turned ON (instead of waiting
     * up to 15 minutes for the backfill scheduler). Dispatch is only attempted
     * when the toggle is ON.
     *
     * @param unit             the attachment video unit to process
     * @param stateToDelete    if present, delete this state after preflight checks pass (used by retryProcessing)
     * @param dispatchPriority queue priority to enqueue with (fresh work before backlog)
     * @return true if preflight checks passed, false if preflight failed
     */
    private boolean doTriggerProcessing(AttachmentVideoUnit unit, Optional<LectureUnitProcessingState> stateToDelete, int dispatchPriority) {
        if (unit == null || unit.getId() == null) {
            log.warn("Cannot process null or unsaved lecture unit");
            return false;
        }

        if (unit.getLecture() != null && unit.getLecture().isTutorialLecture()) {
            log.debug("Skipping processing for tutorial lecture unit: {}", unit.getId());
            return false;
        }

        boolean hasVideo = unit.getVideoSource() != null && !unit.getVideoSource().isBlank();
        boolean hasPdf = unit.getAttachment() != null && unit.getAttachment().getLink() != null && unit.getAttachment().getLink().endsWith(".pdf");
        Optional<LectureUnitProcessingState> existingState = stateToDelete.isPresent() ? Optional.empty() : processingStateRepository.findByLectureUnit_Id(unit.getId());

        if (!hasVideo && !hasPdf) {
            existingState.ifPresent(state -> cleanupRemovedProcessableContent(unit, state));
            log.debug("Unit {} has no video or PDF to process", unit.getId());
            return false;
        }

        if (irisLectureApi.isEmpty()) {
            log.debug("Iris not available, skipping processing for unit {}", unit.getId());
            return false;
        }

        // Preflight passed - handle existing state
        if (stateToDelete.isPresent()) {
            processingStateRepository.delete(stateToDelete.get());
        }

        LectureUnitProcessingState state = existingState.orElseGet(() -> new LectureUnitProcessingState(unit));

        // A retry replaces the failed row rather than reusing it, which would restart the transcription version at 1. Unlike the attachment version, which can always be
        // read back from the attachment itself, this counter has no other home: losing it would make an Iris citation pinned to version 1 look current again after the
        // next transcription, and send a student to a timestamp in material that has since changed. It is carried over so the count stays monotonic across retries.
        stateToDelete.ifPresent(failedState -> {
            state.setTranscriptionVersion(failedState.getTranscriptionVersion());
            state.setTranscriptionContentHash(failedState.getTranscriptionContentHash());
        });

        // Detect content changes
        boolean contentChanged = handleContentChanges(unit, state, hasVideo, hasPdf);
        boolean shouldReprocess = contentChanged;
        if (shouldReprocess) {
            state.resetRetryCount();
            state.requeue();
            // The previous run's fingerprints describe content that no longer exists in this form.
            // Leaving them would let the reconciler treat the unit as verified against stale evidence.
            state.setContentFingerprint(null);
            state.setConfirmedFingerprint(null);
        }

        // Skip if already processing or terminal (unchanged content)
        if (state.isProcessing()) {
            log.debug("Unit {} already processing, skipping", unit.getId());
            return true;
        }
        if (state.getPhase() == ProcessingPhase.DONE) {
            log.debug("Unit {} already done, skipping", unit.getId());
            return true;
        }
        if (state.getPhase() == ProcessingPhase.FAILED) {
            log.debug("Unit {} in failed state, skipping (use retryProcessing or change content)", unit.getId());
            return true;
        }
        if (state.getPhase() == ProcessingPhase.SKIPPED) {
            log.debug("Unit {} was declined by Iris, awaiting a content change before re-evaluation", unit.getId());
            return true;
        }

        // Enqueue. A new row has nothing to race with, so it is saved outright; an existing one is committed through
        // a targeted update instead, because the snapshot in hand predates the content check and a whole-entity save
        // would revert a run claimed and activated while that check ran.
        state.setDispatchPriority(dispatchPriority);
        if (state.getId() == null) {
            state.setStartedAt(null); // Ensure new states have no startedAt
            processingStateRepository.save(state);
        }
        else if (shouldReprocess) {
            processingStateRepository.requeueForContentChange(state.getId(), state.getVideoSourceHash(), state.getAttachmentVersion(), dispatchPriority, ZonedDateTime.now());
        }
        else {
            processingStateRepository.updateContentMarkers(state.getId(), state.getVideoSourceHash(), state.getAttachmentVersion(), dispatchPriority, ZonedDateTime.now());
        }
        log.info("Enqueued unit {} for processing (IDLE)", unit.getId());

        // Only dispatch if the feature toggle is ON — otherwise the IDLE state
        // waits in the queue and gets dispatched when the toggle is turned ON
        // (picked up by the scheduler within 5 minutes).
        if (featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)) {
            processingStateCallbackService.dispatchPendingJobs();
        }
        else {
            log.info("Feature toggle OFF — unit {} queued as IDLE, will dispatch when toggle is enabled", unit.getId());
        }
        return true;
    }

    /**
     * Clean up external resources when lecture units are being deleted.
     * Removes all content from the Iris vector database in a single batch call.
     *
     * @param units the attachment video units being deleted
     */
    @Async
    public void handleUnitsDeletion(List<AttachmentVideoUnit> units) {
        SecurityUtils.setAuthorizationObject();
        deleteUnitsFromPyris(units);
    }

    private void deleteUnitsFromPyris(List<AttachmentVideoUnit> units) {
        if (units == null || units.isEmpty()) {
            return;
        }

        log.info("Handling deletion cleanup for {} units", units.size());

        if (irisLectureApi.isPresent()) {
            try {
                irisLectureApi.get().deleteLectureFromPyrisDB(units);
                log.info("Deleted {} units from Iris", units.size());
            }
            catch (Exception e) {
                log.warn("Failed to delete units from Iris: {}", e.getMessage());
            }
        }
    }

    /**
     * Manually retry processing for a unit that failed.
     * Deletes the FAILED state, creates a fresh IDLE state, and triggers dispatch.
     *
     * @param lectureUnit the unit to retry (must be in FAILED state)
     * @return the processing state after retry attempt, or null if retry not possible
     */
    @Nullable
    public LectureUnitProcessingState retryProcessing(AttachmentVideoUnit lectureUnit) {
        if (lectureUnit == null || lectureUnit.getId() == null) {
            log.warn("Cannot retry processing for null or unsaved lecture unit");
            return null;
        }

        var existingState = processingStateRepository.findByLectureUnit_Id(lectureUnit.getId());
        if (existingState.isEmpty() || existingState.get().getPhase() != ProcessingPhase.FAILED) {
            return null;
        }

        log.info("Retrying processing for unit {}", lectureUnit.getId());

        // Run processing, passing the FAILED state to delete after preflight passes
        // If preflight fails, the FAILED state is preserved (nothing deleted)
        // If preflight passes, the FAILED state is deleted and fresh state created
        boolean preflightPassed = doTriggerProcessing(lectureUnit, existingState, FRESH_DISPATCH_PRIORITY);
        if (!preflightPassed) {
            // Services unavailable - FAILED state was NOT deleted, return null
            return null;
        }

        // Preflight passed, old state was deleted - check what was created
        var newState = processingStateRepository.findByLectureUnit_Id(lectureUnit.getId());
        if (newState.isPresent()) {
            return newState.get();
        }

        // Processing couldn't start — create a FAILED state to provide feedback
        var failedState = new LectureUnitProcessingState(lectureUnit);
        failedState.markFailed("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        return processingStateRepository.save(failedState);
    }

    /**
     * Get the current processing state for a lecture unit.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @return the processing state, or empty if not found
     */
    public Optional<LectureUnitProcessingState> getProcessingState(Long lectureUnitId) {
        return processingStateRepository.findByLectureUnit_Id(lectureUnitId);
    }

    // -------------------- Retry (no longer phase-specific) --------------------
    // Retries are handled by resetting to IDLE and going through dispatchPendingJobs().
    // See ProcessingStateCallbackService.handleProcessingFailureIfStillLive().

    // -------------------- Helper Methods --------------------

    /**
     * Detect content changes and perform cleanup if needed.
     * Only returns true if existing content changed (not for new units being processed for the first time).
     *
     * @return true if content changed and cleanup was performed
     */
    private boolean handleContentChanges(AttachmentVideoUnit unit, LectureUnitProcessingState state, boolean hasVideo, boolean hasPdf) {
        String currentVideoHash = computeHash(unit.getVideoSource());
        Integer currentAttachmentVersion = unit.getAttachment() != null ? unit.getAttachment().getVersion() : null;

        boolean hasPersistedState = state.getId() != null;
        boolean previousVideoKnown = state.getVideoSourceHash() != null && !state.getVideoSourceHash().isBlank();
        boolean previousAttachmentKnown = state.getAttachmentVersion() != null;
        boolean videoAdded = hasPersistedState && hasVideo && !previousVideoKnown;
        boolean videoRemoved = hasPersistedState && !hasVideo && previousVideoKnown;
        boolean videoChanged = hasVideo && previousVideoKnown && !currentVideoHash.equals(state.getVideoSourceHash());
        boolean attachmentAdded = hasPersistedState && hasPdf && !previousAttachmentKnown;
        boolean attachmentRemoved = hasPersistedState && !hasPdf && previousAttachmentKnown;
        boolean attachmentChanged = hasPdf && previousAttachmentKnown && !state.getAttachmentVersion().equals(currentAttachmentVersion);

        // For new units, just set the hash/version without triggering cleanup
        if (hasVideo && state.getVideoSourceHash() == null) {
            state.setVideoSourceHash(currentVideoHash);
        }
        if (hasPdf && state.getAttachmentVersion() == null) {
            state.setAttachmentVersion(currentAttachmentVersion);
        }

        if (videoAdded || videoRemoved || videoChanged || attachmentAdded || attachmentRemoved || attachmentChanged) {
            log.info("Content changed for unit {}, videoAdded: {}, videoRemoved: {}, videoChanged: {}, attachmentAdded: {}, attachmentRemoved: {}, attachmentChanged: {}",
                    unit.getId(), videoAdded, videoRemoved, videoChanged, attachmentAdded, attachmentRemoved, attachmentChanged);

            // Invalidate any in-flight run's token before the cleanup below deletes stored content: a
            // checkpoint still holding this token then fails its own token-match check immediately,
            // instead of succeeding on a stale snapshot and persisting content this cleanup is about
            // to remove. The later requeueForContentChange this method's caller performs still clears
            // the token again (harmless): this call only narrows the window before that happens.
            String staleToken = state.getIngestionJobToken();
            if (hasPersistedState && staleToken != null) {
                processingStateRepository.invalidateTokenIfMatches(state.getId(), staleToken, ZonedDateTime.now());
            }

            if (videoAdded || videoRemoved || videoChanged) {
                // Delete stored transcription before Iris cleanup: dispatchPendingJobs() checks
                // for a COMPLETED transcription to decide whether to skip straight to INGESTING.
                // Leaving the old record would cause stale text from the previous video to be ingested.
                processingStateCallbackService.deleteTranscriptionForUnit(unit.getId());
                cleanupForReprocessing(unit);
                state.setVideoSourceHash(hasVideo ? currentVideoHash : null);
            }

            if ((attachmentAdded || attachmentRemoved || attachmentChanged) && !(videoAdded || videoRemoved || videoChanged)) {
                cleanupForReprocessing(unit);
            }

            state.setAttachmentVersion(hasPdf ? currentAttachmentVersion : null);
            return true;
        }

        return false;
    }

    /**
     * Whether the unit's video source differs from the one its processing state last recorded: added, removed or
     * replaced, by the same rule {@link #handleContentChanges} applies. When it does, a stored transcript belongs to the
     * previous video, so a repair must go through the content-change path, which deletes it, rather than a plain requeue.
     *
     * @param unit  the unit as it currently is
     * @param state its processing state, carrying the recorded video marker
     * @return true if the video source changed since the state's marker was recorded
     */
    public boolean hasVideoSourceChanged(AttachmentVideoUnit unit, LectureUnitProcessingState state) {
        boolean hasVideo = unit.getVideoSource() != null && !unit.getVideoSource().isBlank();
        boolean previousVideoKnown = state.getVideoSourceHash() != null && !state.getVideoSourceHash().isBlank();
        if (!hasVideo) {
            return previousVideoKnown;
        }
        return !previousVideoKnown || !computeHash(unit.getVideoSource()).equals(state.getVideoSourceHash());
    }

    private void cleanupRemovedProcessableContent(AttachmentVideoUnit unit, LectureUnitProcessingState state) {
        boolean previousVideoKnown = state.getVideoSourceHash() != null && !state.getVideoSourceHash().isBlank();
        boolean previousAttachmentKnown = state.getAttachmentVersion() != null;
        if (!previousVideoKnown && !previousAttachmentKnown) {
            return;
        }

        log.info("Processable content removed for unit {}, cleaning up existing Pyris content and processing state", unit.getId());
        // As in handleContentChanges: invalidate an in-flight run before deleting what it is producing, so it cannot write its
        // transcript back or finish as DONE for removed content. If the cleanup then fails, the row is stranded in flight, which
        // the scheduler's recovery sweep settles once the Iris cleanup succeeds.
        String staleToken = state.getIngestionJobToken();
        if (staleToken != null) {
            processingStateRepository.invalidateTokenIfMatches(state.getId(), staleToken, ZonedDateTime.now());
        }
        if (previousVideoKnown) {
            processingStateCallbackService.deleteTranscriptionForUnit(unit.getId());
        }
        boolean cleanupSucceeded = cleanupForReprocessing(unit);
        if (!cleanupSucceeded) {
            log.warn("Cleanup delete failed for unit {}, preserving stored content markers for a future retry", unit.getId());
            return;
        }

        // DONE here means "nothing indexed"; a confirmed fingerprint from the previous content would falsely claim
        // the index still holds verified data for this unit, so it is dropped with the rest. Committed as a targeted
        // update rather than a whole-entity save, which would revert a run activated while the cleanup above ran.
        processingStateRepository.settleAsNothingIndexed(state.getId(), ZonedDateTime.now());
    }

    /**
     * Finish a content change that stopped after the in-flight run's token was invalidated, for a row the caller has claimed
     * through {@link LectureUnitProcessingStateRecoveryRepository#claimStrandedRun}. Mirrors the two outcomes of an edit: the
     * unit has content, so the previous video's transcript goes if the video changed and the unit is requeued for its current
     * content; or it has none, so its Iris content goes and the row settles as nothing indexed. Every write is pinned to the
     * claim, so if a concurrent edit requeues the unit in the meantime (which clears the claim), the rest of this does nothing
     * and cannot touch the newer run. A failed Iris cleanup keeps the claim, so the sweep retries once the cutoff passes again.
     *
     * @param unit       the unit, loaded after the claim
     * @param claimToken the claim the caller holds on the unit's processing state
     */
    public void recoverInterruptedContentChange(AttachmentVideoUnit unit, String claimToken) {
        LectureUnitProcessingState state = processingStateRepository.findByLectureUnit_Id(unit.getId()).orElse(null);
        if (state == null || !claimToken.equals(state.getClaimToken())) {
            return; // Requeued by an edit since the claim; the guards below would match nothing anyway
        }
        boolean hasVideo = unit.getVideoSource() != null && !unit.getVideoSource().isBlank();
        boolean hasPdf = unit.getAttachment() != null && unit.getAttachment().getLink() != null && unit.getAttachment().getLink().endsWith(".pdf");
        if (hasVideoSourceChanged(unit, state)) {
            // The stored transcript belongs to a video the unit no longer has
            transcriptionRepository.deleteIfRecoveryClaimHolds(unit.getId(), claimToken);
        }
        boolean cleanupSucceeded = cleanupForReprocessing(unit);
        if (!hasVideo && !hasPdf) {
            if (cleanupSucceeded) {
                recoveryRepository.settleStrandedRunIfClaimed(state.getId(), claimToken, ZonedDateTime.now());
            }
            return;
        }
        recoveryRepository.requeueStrandedRunIfClaimed(state.getId(), claimToken, hasVideo ? computeHash(unit.getVideoSource()) : null,
                hasPdf ? unit.getAttachment().getVersion() : null, FRESH_DISPATCH_PRIORITY, ZonedDateTime.now());
    }

    private boolean cleanupForReprocessing(AttachmentVideoUnit unit) {
        Attachment attachment = unit.getAttachment();
        if (attachment != null && attachment.getDisplayPageNumbers() != null) {
            log.info("Clearing existing display page numbers for unit {} (content changed)", unit.getId());
            // A field-only update: saving the whole attachment from this snapshot could revert a version uploaded meanwhile
            attachmentRepository.updateDisplayPageNumbers(attachment.getId(), null);
        }

        // When a new job starts, Iris terminates old processes automatically
        if (irisLectureApi.isEmpty()) {
            log.warn("Cannot delete unit {} from Iris because the lecture API is unavailable", unit.getId());
            return false;
        }
        try {
            irisLectureApi.get().deleteLectureFromPyrisDB(List.of(unit));
            log.info("Deleted unit {} from Iris vector DB", unit.getId());
            return true;
        }
        catch (Exception e) {
            log.warn("Failed to delete unit {} from Iris: {}", unit.getId(), e.getMessage());
            return false;
        }
    }

    private String computeHash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
