package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.nebula.api.LectureTranscriptionApi;
import de.tum.cit.aet.artemis.nebula.api.TumLiveApi;

/**
 * Service that orchestrates the automated lecture content processing pipeline.
 * This includes:
 * <ul>
 * <li>Checking for TUM Live playlist availability</li>
 * <li>Triggering transcription generation via Nebula</li>
 * <li>Triggering ingestion into Pyris/Iris</li>
 * <li>Handling content changes and cancellation</li>
 * <li>Managing retry logic</li>
 * </ul>
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class LectureContentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LectureContentProcessingService.class);

    private static final int MAX_RETRIES = 3;

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    private final Optional<LectureTranscriptionApi> transcriptionApi;

    private final Optional<TumLiveApi> tumLiveApi;

    private final Optional<IrisLectureApi> irisLectureApi;

    public LectureContentProcessingService(LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository,
            Optional<LectureTranscriptionApi> transcriptionApi, Optional<TumLiveApi> tumLiveApi, Optional<IrisLectureApi> irisLectureApi) {
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.transcriptionApi = transcriptionApi;
        this.tumLiveApi = tumLiveApi;
        this.irisLectureApi = irisLectureApi;
    }

    /**
     * Main entry point: Trigger processing for an AttachmentVideoUnit.
     * This is called when a unit is created or updated.
     * <p>
     * The method detects content changes (video URL or attachment version) and
     * restarts processing from the appropriate phase.
     * <p>
     * Uses database locking to prevent concurrent calls for the same unit from
     * causing duplicate processing jobs.
     * <p>
     * Processing behavior depends on available services:
     * <ul>
     * <li>Nebula ON, Iris ON: Full pipeline (transcription + ingestion)</li>
     * <li>Nebula ON, Iris OFF: Transcription only (transcripts are still useful)</li>
     * <li>Nebula OFF, Iris ON: Ingestion only (PDF ingestion)</li>
     * <li>Neither available: Skip processing entirely</li>
     * </ul>
     *
     * @param unit the attachment video unit to process
     */
    @Transactional
    public void triggerProcessing(AttachmentVideoUnit unit) {
        if (unit == null || unit.getId() == null) {
            log.warn("Cannot process null or unsaved lecture unit");
            return;
        }

        // Skip tutorial lectures - they're not meant for processing
        if (unit.getLecture() != null && unit.getLecture().isTutorialLecture()) {
            log.debug("Skipping processing for tutorial lecture unit: {}", unit.getId());
            return;
        }

        // Check if we have any content to process
        boolean hasVideo = unit.getVideoSource() != null && !unit.getVideoSource().isBlank();
        boolean hasPdf = unit.getAttachment() != null && unit.getAttachment().getLink() != null && unit.getAttachment().getLink().endsWith(".pdf");

        if (!hasVideo && !hasPdf) {
            log.debug("Unit {} has no video or PDF to process", unit.getId());
            return;
        }

        // Check if we have any services to do processing
        boolean canTranscribe = hasVideo && transcriptionApi.isPresent() && tumLiveApi.isPresent();
        boolean canIngest = irisLectureApi.isPresent() && (hasPdf || canTranscribe);

        if (!canTranscribe && !canIngest) {
            log.debug("No processing services available for unit {} (Nebula: {}, Iris: {})", unit.getId(), transcriptionApi.isPresent(), irisLectureApi.isPresent());
            return;
        }

        LectureUnitProcessingState state = getOrCreateProcessingState(unit);

        // Detect content changes
        String currentVideoHash = computeHash(unit.getVideoSource());
        Integer currentAttachmentVersion = unit.getAttachment() != null ? unit.getAttachment().getVersion() : null;

        boolean videoChanged = hasVideo && !currentVideoHash.equals(state.getVideoSourceHash());
        boolean attachmentChanged = hasPdf && (state.getAttachmentVersion() == null || !state.getAttachmentVersion().equals(currentAttachmentVersion));

        if (videoChanged || attachmentChanged) {
            log.info("Content changed for unit {}, video changed: {}, attachment changed: {}", unit.getId(), videoChanged, attachmentChanged);

            // If video changed, we need to redo transcription and delete old data
            if (videoChanged && hasVideo) {
                cleanupForReprocessing(unit, true);
                state.setVideoSourceHash(currentVideoHash);
            }

            // If only attachment changed, just re-ingest
            if (attachmentChanged && !videoChanged) {
                cleanupForReprocessing(unit, false);
            }

            state.setAttachmentVersion(currentAttachmentVersion);
            state.resetRetryCount();
        }

        // If already processing or done, don't restart unless content changed
        if (state.isProcessing() && !videoChanged && !attachmentChanged) {
            log.debug("Unit {} already processing, skipping", unit.getId());
            return;
        }

        if (state.getPhase() == ProcessingPhase.DONE && !videoChanged && !attachmentChanged) {
            log.debug("Unit {} already done, skipping", unit.getId());
            return;
        }

        // Start processing
        startProcessing(unit, state, hasVideo, hasPdf);
    }

    /**
     * Cancel any ongoing processing for a lecture unit.
     * Called when the unit is being deleted or when content is changing.
     * This will cancel any running transcription job on Nebula and ingestion job on Pyris.
     *
     * @param lectureUnitId the ID of the lecture unit
     */
    public void cancelProcessing(Long lectureUnitId) {
        processingStateRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(state -> {
            log.info("Cancelling processing for unit {}", lectureUnitId);

            // If we're in transcription phase, cancel the job on Nebula
            if (state.getPhase() == ProcessingPhase.TRANSCRIBING) {
                cancelTranscriptionOnNebula(lectureUnitId);
            }

            // If we're in ingestion phase, cancel the pending Pyris job
            if (state.getPhase() == ProcessingPhase.INGESTING) {
                cancelIngestionOnPyris(lectureUnitId);
            }

            state.transitionTo(ProcessingPhase.IDLE);
            state.setErrorMessage("Cancelled");
            processingStateRepository.save(state);
        });
    }

    /**
     * Cancel a running transcription job on Nebula.
     * Silently handles errors since cancellation is best-effort.
     *
     * @param lectureUnitId the ID of the lecture unit
     */
    private void cancelTranscriptionOnNebula(Long lectureUnitId) {
        if (transcriptionApi.isEmpty()) {
            log.debug("Transcription API not available, cannot cancel on Nebula");
            return;
        }

        try {
            transcriptionApi.get().cancelNebulaTranscription(lectureUnitId);
            log.info("Cancelled transcription on Nebula for unit {}", lectureUnitId);
        }
        catch (Exception e) {
            // Log but don't fail - cancellation is best-effort
            log.warn("Failed to cancel transcription on Nebula for unit {}: {}", lectureUnitId, e.getMessage());
        }
    }

    /**
     * Cancel a pending ingestion job on Pyris.
     * This removes the job from the tracking map, so the webhook callback will be ignored.
     * Silently handles errors since cancellation is best-effort.
     *
     * @param lectureUnitId the ID of the lecture unit
     */
    private void cancelIngestionOnPyris(Long lectureUnitId) {
        if (irisLectureApi.isEmpty()) {
            log.debug("Iris API not available, cannot cancel on Pyris");
            return;
        }

        try {
            boolean cancelled = irisLectureApi.get().cancelPendingIngestion(lectureUnitId);
            if (cancelled) {
                log.info("Cancelled pending ingestion on Pyris for unit {}", lectureUnitId);
            }
        }
        catch (Exception e) {
            // Log but don't fail - cancellation is best-effort
            log.warn("Failed to cancel ingestion on Pyris for unit {}: {}", lectureUnitId, e.getMessage());
        }
    }

    /**
     * Called when a transcription completes (from the polling scheduler).
     * This advances the processing to the ingestion phase.
     * <p>
     * Includes race condition protection: if the content changed since this transcription
     * started, it may be stale and should be ignored.
     *
     * @param transcription the completed transcription
     */
    public void handleTranscriptionComplete(LectureTranscription transcription) {
        if (transcription.getLectureUnit() == null) {
            log.warn("Transcription {} has no associated lecture unit", transcription.getId());
            return;
        }

        Long unitId = transcription.getLectureUnit().getId();
        processingStateRepository.findByLectureUnit_Id(unitId).ifPresent(state -> {
            if (state.getPhase() == ProcessingPhase.TRANSCRIBING) {
                // Race condition check: verify this is still the current transcription
                // If video was changed, a new transcription may have been started
                Optional<LectureTranscription> currentTranscription = transcriptionRepository.findByLectureUnit_Id(unitId);
                if (currentTranscription.isEmpty() || !currentTranscription.get().getId().equals(transcription.getId())) {
                    log.warn("Ignoring stale transcription callback for unit {} (transcription ID {} is no longer current)", unitId, transcription.getId());
                    return;
                }

                if (transcription.getTranscriptionStatus() == TranscriptionStatus.COMPLETED) {
                    log.info("Transcription completed for unit {}", unitId);
                    // If Iris is available, proceed to ingestion; otherwise we're done
                    if (irisLectureApi.isPresent()) {
                        startIngestion(state);
                    }
                    else {
                        log.info("Iris not available, marking unit {} as done after transcription", unitId);
                        state.transitionTo(ProcessingPhase.DONE);
                        processingStateRepository.save(state);
                    }
                }
                else if (transcription.getTranscriptionStatus() == TranscriptionStatus.FAILED) {
                    log.warn("Transcription failed for unit {}", unitId);
                    handleTranscriptionFailure(state);
                }
            }
        });
    }

    /**
     * Called when ingestion completes (from the Pyris webhook callback).
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param success       whether ingestion succeeded
     */
    public void handleIngestionComplete(Long lectureUnitId, boolean success) {
        Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByLectureUnit_Id(lectureUnitId);

        if (stateOpt.isEmpty()) {
            // Log warning if state not found - unit may have been deleted during processing
            log.warn("Received ingestion callback for unit {}, but no processing state exists (unit may have been deleted)", lectureUnitId);
            return;
        }

        LectureUnitProcessingState state = stateOpt.get();
        if (state.getPhase() != ProcessingPhase.INGESTING) {
            // Log warning if phase doesn't match - may indicate a stale callback
            log.warn("Received ingestion callback for unit {} in phase {} (expected INGESTING), ignoring", lectureUnitId, state.getPhase());
            return;
        }

        if (success) {
            log.info("Ingestion completed successfully for unit {}", lectureUnitId);
            state.transitionTo(ProcessingPhase.DONE);
        }
        else {
            log.warn("Ingestion failed for unit {}", lectureUnitId);
            handleIngestionFailure(state);
        }
        processingStateRepository.save(state);
    }

    /**
     * Manually retry processing for a unit that failed.
     *
     * @param lectureUnit the unit to retry
     */
    public void retryProcessing(AttachmentVideoUnit lectureUnit) {
        processingStateRepository.findByLectureUnit_Id(lectureUnit.getId()).ifPresent(state -> {
            if (state.getPhase() == ProcessingPhase.FAILED) {
                log.info("Retrying processing for unit {}", lectureUnit.getId());
                state.resetRetryCount();
                state.transitionTo(ProcessingPhase.IDLE);
                processingStateRepository.save(state);

                // Re-trigger processing
                triggerProcessing(lectureUnit);
            }
        });
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

    /**
     * Delete the processing state for a lecture unit (called during deletion).
     *
     * @param lectureUnitId the ID of the lecture unit
     */
    public void deleteProcessingState(Long lectureUnitId) {
        processingStateRepository.deleteByLectureUnit_Id(lectureUnitId);
    }

    // -------------------- Private Helper Methods --------------------

    /**
     * Get or create processing state with pessimistic lock to prevent concurrent modifications.
     * Must be called within a transaction.
     */
    private LectureUnitProcessingState getOrCreateProcessingState(LectureUnit unit) {
        // Use locking query to prevent concurrent modifications
        return processingStateRepository.findByLectureUnitIdWithLock(unit.getId()).orElseGet(() -> {
            LectureUnitProcessingState state = new LectureUnitProcessingState(unit);
            return processingStateRepository.save(state);
        });
    }

    private void startProcessing(AttachmentVideoUnit unit, LectureUnitProcessingState state, boolean hasVideo, boolean hasPdf) {
        log.info("Starting processing for unit {}", unit.getId());

        if (hasVideo) {
            // Check for playlist availability
            state.transitionTo(ProcessingPhase.CHECKING_PLAYLIST);
            processingStateRepository.save(state);

            checkPlaylistAndContinue(unit, state, hasPdf);
        }
        else if (hasPdf) {
            // No video, just ingest the PDF
            startIngestion(state);
        }
    }

    private void checkPlaylistAndContinue(AttachmentVideoUnit unit, LectureUnitProcessingState state, boolean hasPdf) {
        if (tumLiveApi.isEmpty()) {
            log.debug("TUM Live service not available, skipping transcription for unit {}", unit.getId());
            // No transcription possible, go directly to ingestion
            if (hasPdf) {
                startIngestion(state);
            }
            else {
                state.transitionTo(ProcessingPhase.IDLE);
                processingStateRepository.save(state);
            }
            return;
        }

        String videoUrl = unit.getVideoSource();
        Optional<String> playlistUrl;
        try {
            playlistUrl = tumLiveApi.get().getTumLivePlaylistLink(videoUrl);
        }
        catch (Exception e) {
            log.error("Failed to fetch playlist URL for unit {}: {}", unit.getId(), e.getMessage());
            // Fall back to PDF-only if available, otherwise reset to IDLE
            if (hasPdf) {
                log.info("Playlist check failed, proceeding with PDF-only ingestion for unit {}", unit.getId());
                startIngestion(state);
            }
            else {
                state.transitionTo(ProcessingPhase.IDLE);
                state.setErrorMessage("Playlist check failed: " + e.getMessage());
                processingStateRepository.save(state);
            }
            return;
        }

        if (playlistUrl.isPresent()) {
            log.info("Playlist URL found for unit {}, starting transcription", unit.getId());
            // Store playlist URL for retry purposes
            state.setPlaylistUrl(playlistUrl.get());
            processingStateRepository.save(state);
            startTranscription(unit, state, playlistUrl.get());
        }
        else {
            log.debug("No playlist URL found for unit {}, skipping transcription", unit.getId());
            // No transcription possible, go to ingestion if we have PDF
            if (hasPdf) {
                startIngestion(state);
            }
            else {
                state.transitionTo(ProcessingPhase.IDLE);
                state.setErrorMessage("No playlist URL available for transcription");
                processingStateRepository.save(state);
            }
        }
    }

    private void startTranscription(AttachmentVideoUnit unit, LectureUnitProcessingState state, String playlistUrl) {
        if (transcriptionApi.isEmpty()) {
            // Nebula (transcription service) is not available - this is not an error, just skip transcription
            log.debug("Transcription service (Nebula) not available, falling back to PDF-only processing for unit {}", unit.getId());
            // Graceful degradation: if transcription service isn't available but we have a PDF,
            // proceed with ingestion of just the PDF
            if (unit.getAttachment() != null && unit.getAttachment().getLink() != null) {
                startIngestion(state);
            }
            else {
                // No transcription possible and no PDF - nothing to ingest
                state.transitionTo(ProcessingPhase.IDLE);
                state.setErrorMessage(null); // Not a failure, just nothing to do
                processingStateRepository.save(state);
            }
            return;
        }

        try {
            state.transitionTo(ProcessingPhase.TRANSCRIBING);
            processingStateRepository.save(state);

            NebulaTranscriptionRequestDTO request = new NebulaTranscriptionRequestDTO(playlistUrl, unit.getLecture().getId(), unit.getId());

            transcriptionApi.get().startNebulaTranscription(unit.getLecture().getId(), unit.getId(), request);

            log.info("Transcription job started for unit {}", unit.getId());
        }
        catch (Exception e) {
            log.error("Failed to start transcription for unit {}: {}", unit.getId(), e.getMessage());
            handleTranscriptionFailure(state);
        }
    }

    private void handleTranscriptionFailure(LectureUnitProcessingState state) {
        state.incrementRetryCount();

        if (state.getRetryCount() >= MAX_RETRIES) {
            // After max retries, fall back to PDF-only if available AND Iris is available, otherwise fail
            LectureUnit unit = state.getLectureUnit();
            boolean hasPdf = unit instanceof AttachmentVideoUnit attachmentUnit && attachmentUnit.getAttachment() != null && attachmentUnit.getAttachment().getLink() != null
                    && attachmentUnit.getAttachment().getLink().endsWith(".pdf");

            if (hasPdf && irisLectureApi.isPresent()) {
                log.info("Max retries reached for transcription, falling back to PDF-only ingestion for unit {}", unit.getId());
                state.resetRetryCount(); // Reset for ingestion retries
                startIngestion(state);
            }
            else {
                log.warn("Max retries reached for transcription, marking as failed for unit {}", unit.getId());
                state.markFailed("Transcription failed after " + MAX_RETRIES + " attempts");
                processingStateRepository.save(state);
            }
        }
        else {
            // Retry transcription using stored playlist URL
            String playlistUrl = state.getPlaylistUrl();
            if (playlistUrl != null && !playlistUrl.isBlank()) {
                log.info("Retrying transcription for unit {} (attempt {})", state.getLectureUnit().getId(), state.getRetryCount());
                LectureUnit unit = state.getLectureUnit();
                if (unit instanceof AttachmentVideoUnit attachmentUnit) {
                    processingStateRepository.save(state);
                    startTranscription(attachmentUnit, state, playlistUrl);
                }
                else {
                    state.markFailed("Invalid unit type for transcription retry");
                    processingStateRepository.save(state);
                }
            }
            else {
                // No playlist URL stored, fall back to PDF-only if available AND Iris is available
                LectureUnit unit = state.getLectureUnit();
                if (unit instanceof AttachmentVideoUnit attachmentUnit && attachmentUnit.getAttachment() != null && irisLectureApi.isPresent()) {
                    log.info("No playlist URL for retry, falling back to PDF-only ingestion");
                    startIngestion(state);
                }
                else {
                    state.markFailed("Transcription failed and no fallback available");
                    processingStateRepository.save(state);
                }
            }
        }
    }

    private void startIngestion(LectureUnitProcessingState state) {
        if (irisLectureApi.isEmpty()) {
            // Iris is intentionally disabled - don't mark as failed, just return to idle
            // This can happen if Iris was disabled mid-processing (unlikely but possible)
            log.debug("Iris API not available, skipping ingestion for unit {}", state.getLectureUnit().getId());
            state.transitionTo(ProcessingPhase.IDLE);
            state.setErrorMessage(null); // Clear any previous error - this is not a failure
            processingStateRepository.save(state);
            return;
        }

        LectureUnit unit = state.getLectureUnit();
        if (!(unit instanceof AttachmentVideoUnit attachmentUnit)) {
            log.warn("Cannot ingest non-AttachmentVideoUnit");
            state.markFailed("Invalid unit type for ingestion");
            processingStateRepository.save(state);
            return;
        }

        try {
            state.transitionTo(ProcessingPhase.INGESTING);
            processingStateRepository.save(state);

            String result = irisLectureApi.get().addLectureUnitToPyrisDB(attachmentUnit);

            if (result != null) {
                log.info("Ingestion started for unit {}", unit.getId());
                // The webhook callback will mark it as DONE
            }
            else {
                log.warn("Ingestion returned null for unit {}", unit.getId());
                handleIngestionFailure(state);
            }
        }
        catch (Exception e) {
            log.error("Failed to start ingestion for unit {}: {}", unit.getId(), e.getMessage());
            handleIngestionFailure(state);
        }
    }

    private void handleIngestionFailure(LectureUnitProcessingState state) {
        state.incrementRetryCount();

        if (state.getRetryCount() >= MAX_RETRIES) {
            log.warn("Max retries reached for ingestion, marking as failed");
            state.markFailed("Ingestion failed after " + MAX_RETRIES + " attempts");
            processingStateRepository.save(state);
        }
        else {
            log.info("Retrying ingestion for unit {} (attempt {})", state.getLectureUnit().getId(), state.getRetryCount());
            // Immediately retry ingestion
            processingStateRepository.save(state);
            startIngestion(state);
        }
    }

    private void cleanupForReprocessing(AttachmentVideoUnit unit, boolean deleteTranscription) {
        if (deleteTranscription) {
            // IMPORTANT: Delete local transcription FIRST to prevent race conditions
            // If we cancel on Nebula first, there's a window where the scheduler could
            // pick up the old job's completion before we delete the local record
            transcriptionRepository.findByLectureUnit_Id(unit.getId()).ifPresent(transcription -> {
                log.info("Deleting existing transcription for unit {}", unit.getId());
                transcriptionRepository.delete(transcription);
            });

            // Then try to cancel on Nebula (best-effort, may fail if already completed)
            cancelTranscriptionOnNebula(unit.getId());
        }

        // Cancel any pending ingestion job to free up Pyris resources
        cancelIngestionOnPyris(unit.getId());

        // Delete from Pyris
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
            // Fallback to simple hash
            return String.valueOf(value.hashCode());
        }
    }
}
