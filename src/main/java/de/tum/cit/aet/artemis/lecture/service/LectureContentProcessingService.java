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
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
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

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final FeatureToggleService featureToggleService;

    private final ProcessingStateCallbackService processingStateCallbackService;

    public LectureContentProcessingService(LectureUnitProcessingStateRepository processingStateRepository, Optional<IrisLectureApi> irisLectureApi,
            FeatureToggleService featureToggleService, ProcessingStateCallbackService processingStateCallbackService) {
        this.processingStateRepository = processingStateRepository;
        this.irisLectureApi = irisLectureApi;
        this.featureToggleService = featureToggleService;
        this.processingStateCallbackService = processingStateCallbackService;
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
        doTriggerProcessing(unit, Optional.empty());
    }

    /**
     * Core processing logic - enqueues a job as IDLE and triggers dispatch.
     * <p>
     * Always creates the IDLE state even when the feature toggle is OFF, so units
     * are immediately picked up when the toggle is turned ON (instead of waiting
     * up to 15 minutes for the backfill scheduler). Dispatch is only attempted
     * when the toggle is ON.
     *
     * @param unit          the attachment video unit to process
     * @param stateToDelete if present, delete this state after preflight checks pass (used by retryProcessing)
     * @return true if preflight checks passed, false if preflight failed
     */
    private boolean doTriggerProcessing(AttachmentVideoUnit unit, Optional<LectureUnitProcessingState> stateToDelete) {
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

        if (!hasVideo && !hasPdf) {
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

        Optional<LectureUnitProcessingState> existingState = stateToDelete.isPresent() ? Optional.empty() : processingStateRepository.findByLectureUnit_Id(unit.getId());

        LectureUnitProcessingState state = existingState.orElseGet(() -> new LectureUnitProcessingState(unit));

        // Detect content changes
        boolean contentChanged = handleContentChanges(unit, state, hasVideo, hasPdf);
        if (contentChanged) {
            state.resetRetryCount();
            state.setPhase(ProcessingPhase.IDLE);
            state.setStartedAt(null); // Back to queue
            state.setIngestionJobToken(null);
            state.setRetryEligibleAt(null);
            state.setErrorKey(null);
            state.setLastUpdated(ZonedDateTime.now());
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

        // Enqueue: save as IDLE with startedAt=null (not yet dispatched)
        if (state.getId() == null) {
            state.setStartedAt(null); // Ensure new states have no startedAt
        }
        processingStateRepository.save(state);
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
        if (units == null || units.isEmpty()) {
            return;
        }

        SecurityUtils.setAuthorizationObject();
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
        boolean preflightPassed = doTriggerProcessing(lectureUnit, existingState);
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
    // See ProcessingStateCallbackService.handleProcessingFailure().

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

        // Only consider it a "change" if there was previous content (hash/version was set)
        boolean videoChanged = hasVideo && state.getVideoSourceHash() != null && !currentVideoHash.equals(state.getVideoSourceHash());
        boolean attachmentChanged = hasPdf && state.getAttachmentVersion() != null && !state.getAttachmentVersion().equals(currentAttachmentVersion);

        // For new units, just set the hash/version without triggering cleanup
        if (hasVideo && state.getVideoSourceHash() == null) {
            state.setVideoSourceHash(currentVideoHash);
        }
        if (hasPdf && state.getAttachmentVersion() == null) {
            state.setAttachmentVersion(currentAttachmentVersion);
        }

        if (videoChanged || attachmentChanged) {
            log.info("Content changed for unit {}, video: {}, attachment: {}", unit.getId(), videoChanged, attachmentChanged);

            if (videoChanged) {
                // Delete stored transcription before Iris cleanup: dispatchPendingJobs() checks
                // for a COMPLETED transcription to decide whether to skip straight to INGESTING.
                // Leaving the old record would cause stale text from the previous video to be ingested.
                processingStateCallbackService.deleteTranscriptionForUnit(unit.getId());
                cleanupForReprocessing(unit);
                state.setVideoSourceHash(currentVideoHash);
            }

            if (attachmentChanged && !videoChanged) {
                cleanupForReprocessing(unit);
            }

            state.setAttachmentVersion(currentAttachmentVersion);
            return true;
        }

        return false;
    }

    private void cleanupForReprocessing(AttachmentVideoUnit unit) {
        // When a new job starts, Iris terminates old processes automatically
        if (irisLectureApi.isPresent()) {
            try {
                irisLectureApi.get().deleteLectureFromPyrisDB(List.of(unit));
                log.info("Deleted unit {} from Iris vector DB", unit.getId());
            }
            catch (Exception e) {
                log.warn("Failed to delete unit {} from Iris: {}", unit.getId(), e.getMessage());
            }
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
