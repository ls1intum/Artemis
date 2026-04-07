package de.tum.cit.aet.artemis.lecture.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Service that orchestrates the automated lecture content processing pipeline.
 * <p>
 * Uses a state machine pattern to manage processing phases:
 * <ul>
 * <li>IDLE: Initial state, not processing</li>
 * <li>INGESTING: Ingestion in progress with Pyris</li>
 * <li>DONE: Processing completed successfully</li>
 * <li>FAILED: Processing failed after max retries</li>
 * </ul>
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
     * Check if any processing service is available.
     * Used by the scheduler to skip backfill when no services are configured.
     *
     * @return true if ingestion is possible
     */
    public boolean hasProcessingCapabilities() {
        return irisLectureApi.isPresent();
    }

    // -------------------- Public API --------------------

    /**
     * Main entry point: Trigger processing for an AttachmentVideoUnit.
     * Called when a unit is created or updated.
     * <p>
     * Processing behavior depends on available services:
     * <ul>
     * <li>Iris ON: Ingestion (transcription + vector DB)</li>
     * <li>Iris OFF: Skip processing</li>
     * </ul>
     *
     * @param unit the attachment video unit to process
     */
    @Async
    public void triggerProcessing(AttachmentVideoUnit unit) {
        // Set auth context due to @Async
        SecurityUtils.setAuthorizationObject();
        doTriggerProcessing(unit, Optional.empty());
    }

    /**
     * Core processing logic - called by both async triggerProcessing and sync retryProcessing.
     * This method is synchronous; the @Async annotation on triggerProcessing handles async execution.
     *
     * @param unit          the attachment video unit to process
     * @param stateToDelete if present, delete this state after preflight checks pass (used by retryProcessing)
     * @return true if preflight checks passed and processing was attempted, false if preflight failed (nothing changed)
     */
    private boolean doTriggerProcessing(AttachmentVideoUnit unit, Optional<LectureUnitProcessingState> stateToDelete) {
        if (!featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)) {
            log.debug("LectureContentProcessing feature is disabled, skipping processing");
            return false;
        }

        if (unit == null || unit.getId() == null) {
            log.warn("Cannot process null or unsaved lecture unit");
            return false;
        }

        // Skip tutorial lectures
        if (unit.getLecture() != null && unit.getLecture().isTutorialLecture()) {
            log.debug("Skipping processing for tutorial lecture unit: {}", unit.getId());
            return false;
        }

        // Check content availability
        boolean hasVideo = unit.getVideoSource() != null && !unit.getVideoSource().isBlank();
        boolean hasPdf = unit.getAttachment() != null && unit.getAttachment().getLink() != null && unit.getAttachment().getLink().endsWith(".pdf");

        if (!hasVideo && !hasPdf) {
            log.debug("Unit {} has no video or PDF to process", unit.getId());
            return false;
        }

        // Check service availability
        boolean canIngest = irisLectureApi.isPresent() && hasPdf;

        if (!canIngest) {
            log.debug("No processing services available for unit {}", unit.getId());
            return false;
        }

        // Preflight passed - now handle existing state
        if (stateToDelete.isPresent()) {
            processingStateRepository.delete(stateToDelete.get());
        }

        // Query for state (will be empty if we just deleted, or may exist for normal trigger)
        Optional<LectureUnitProcessingState> existingState = stateToDelete.isPresent() ? Optional.empty() : processingStateRepository.findByLectureUnit_Id(unit.getId());

        LectureUnitProcessingState state = existingState.orElseGet(() -> new LectureUnitProcessingState(unit));

        // Detect and handle content changes (updates hash/version in state object)
        boolean contentChanged = handleContentChanges(unit, state, hasVideo, hasPdf);
        if (contentChanged) {
            state.resetRetryCount();
            // Existing states may be DONE/FAILED - transition to IDLE so we reprocess
            // (new states are already IDLE; state is only saved when starting TRANSCRIBING/INGESTING)
            state.transitionTo(ProcessingPhase.IDLE);
        }

        // Check if already processing or in terminal state
        // (content changes already transition to IDLE above, so these checks are for unchanged content)
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

        // Start the state machine (will save state when actually starting processing)
        advanceProcessing(unit, state, hasPdf);
        return true;
    }

    /**
     * Clean up external resources when lecture units are being deleted.
     * This cancels any ongoing processing and removes all content from Pyris in a single batch call.
     *
     * @param units the attachment video units being deleted
     */
    @Async
    public void handleUnitsDeletion(List<AttachmentVideoUnit> units) {
        if (units == null || units.isEmpty()) {
            return;
        }

        // Set auth context due to @Async
        SecurityUtils.setAuthorizationObject();

        log.info("Handling deletion cleanup for {} units", units.size());

        // Batch delete from Pyris vector database
        if (irisLectureApi.isPresent()) {
            try {
                irisLectureApi.get().deleteLectureFromPyrisDB(units);
                log.info("Deleted {} units from Pyris", units.size());
            }
            catch (Exception e) {
                log.warn("Failed to delete units from Pyris: {}", e.getMessage());
            }
        }
    }

    /**
     * Manually retry processing for a unit that failed.
     * This is synchronous - the external API call (Pyris) is just a job submission
     * which is fast. The actual processing happens on Pyris.
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

        // Processing couldn't start (e.g., no playlist available for video)
        // Create a FAILED state to provide feedback to the user
        var failedState = new LectureUnitProcessingState(lectureUnit);
        failedState.markFailed("artemisApp.attachmentVideoUnit.processing.error.noPlaylist");
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

    // -------------------- State Machine --------------------

    /**
     * Advance the processing state machine based on current phase.
     */
    private void advanceProcessing(AttachmentVideoUnit unit, LectureUnitProcessingState state, boolean hasPdf) {
        switch (state.getPhase()) {
            case IDLE -> startProcessingFromIdle(unit, state, hasPdf);
            case TRANSCRIBING, INGESTING -> {
                // Wait for callbacks
            }
            case DONE, FAILED -> {
                // DONE: Already done
                // FAILED: Requires explicit retryProcessing() call (unreachable here due to early return)
            }
        }
    }

    /**
     * Start processing from IDLE state.
     */
    private void startProcessingFromIdle(AttachmentVideoUnit unit, LectureUnitProcessingState state, boolean hasPdf) {
        log.info("Starting processing for unit {}", unit.getId());

        if (hasPdf && irisLectureApi.isPresent()) {
            processingStateCallbackService.startIngestion(state);
        }
        else {
            // Nothing to do - don't persist IDLE state
            // For new states (id=null): state won't be saved, backfill can retry later
            // For existing states in IDLE (content changed but can't process): delete so backfill retries
            // For existing states in other phase: shouldn't happen, but save hash/version updates
            if (state.getId() != null) {
                if (state.getPhase() == ProcessingPhase.IDLE) {
                    processingStateRepository.delete(state);
                    log.debug("Deleted IDLE state for unit {} to allow backfill retry", unit.getId());
                }
                else {
                    processingStateRepository.save(state);
                }
            }
            log.debug("No processing possible for unit {} (no PDF)", unit.getId());
        }
    }

    // -------------------- Phase Handlers --------------------

    /**
     * Retry for a state stuck in the legacy TRANSCRIBING phase.
     * Nebula transcription is no longer used; attempt PDF fallback or mark as failed.
     * Called by the scheduler for states that were in TRANSCRIBING before migration.
     *
     * @param state the processing state to retry
     */
    public void retryTranscription(LectureUnitProcessingState state) {
        LectureUnit unit = state.getLectureUnit();
        if (!(unit instanceof AttachmentVideoUnit attachmentUnit)) {
            state.markFailed("artemisApp.attachmentVideoUnit.processing.error.invalidUnitType");
            processingStateRepository.save(state);
            return;
        }

        state.clearRetryEligibility();

        boolean hasPdf = attachmentUnit.getAttachment() != null && attachmentUnit.getAttachment().getLink() != null && attachmentUnit.getAttachment().getLink().endsWith(".pdf");
        if (hasPdf && irisLectureApi.isPresent()) {
            log.info("Recovering legacy TRANSCRIBING state for unit {}, falling back to PDF ingestion", unit.getId());
            state.resetRetryCount();
            processingStateCallbackService.startIngestion(state);
        }
        else {
            log.warn("Cannot recover legacy TRANSCRIBING state for unit {}, marking as failed", unit.getId());
            state.markFailed("artemisApp.attachmentVideoUnit.processing.error.transcriptionFailed");
            processingStateRepository.save(state);
        }
    }

    /**
     * Retry ingestion for a state that failed.
     * Called by the scheduler after exponential backoff period.
     *
     * @param state the processing state to retry
     */
    public void retryIngestion(LectureUnitProcessingState state) {
        processingStateCallbackService.retryIngestion(state);
    }

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
        // Note: No need to cancel on Pyris - when a new job starts, Pyris terminates old processes automatically
        if (irisLectureApi.isPresent()) {
            try {
                irisLectureApi.get().deleteLectureFromPyrisDB(java.util.List.of(unit));
                log.info("Deleted unit {} from Pyris", unit.getId());
            }
            catch (Exception e) {
                log.warn("Failed to delete unit {} from Pyris: {}", unit.getId(), e.getMessage());
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
