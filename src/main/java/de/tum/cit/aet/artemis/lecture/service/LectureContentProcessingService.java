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
     *
     * @param unit the attachment video unit to process
     */
    public void triggerProcessing(AttachmentVideoUnit unit) {
        if (unit == null || unit.getId() == null) {
            log.warn("Cannot process null or unsaved lecture unit");
            return;
        }

        // Skip tutorial lectures - they're not meant for Iris
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
     * This will cancel any running transcription job on Nebula.
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
     * Called when a transcription completes (from the polling scheduler).
     * This advances the processing to the ingestion phase.
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
                if (transcription.getTranscriptionStatus() == TranscriptionStatus.COMPLETED) {
                    log.info("Transcription completed for unit {}, moving to ingestion", unitId);
                    startIngestion(state);
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
        processingStateRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(state -> {
            if (state.getPhase() == ProcessingPhase.INGESTING) {
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
        });
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

    private LectureUnitProcessingState getOrCreateProcessingState(LectureUnit unit) {
        return processingStateRepository.findByLectureUnit_Id(unit.getId()).orElseGet(() -> {
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
        Optional<String> playlistUrl = tumLiveApi.get().getTumLivePlaylistLink(videoUrl);

        if (playlistUrl.isPresent()) {
            log.info("Playlist URL found for unit {}, starting transcription", unit.getId());
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
            log.warn("Transcription service not available");
            state.markFailed("Transcription service not available");
            processingStateRepository.save(state);
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
            log.warn("Max retries reached for transcription, marking as failed");
            state.markFailed("Transcription failed after " + MAX_RETRIES + " attempts");
        }
        else {
            // Try ingestion without transcription if we have PDF
            LectureUnit unit = state.getLectureUnit();
            if (unit instanceof AttachmentVideoUnit attachmentUnit && attachmentUnit.getAttachment() != null) {
                log.info("Transcription failed, proceeding with PDF-only ingestion");
                startIngestion(state);
            }
            else {
                state.markFailed("Transcription failed and no PDF available");
            }
        }
        processingStateRepository.save(state);
    }

    private void startIngestion(LectureUnitProcessingState state) {
        if (irisLectureApi.isEmpty()) {
            log.debug("Iris API not available, cannot ingest");
            state.markFailed("Iris API not available");
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
        }
        else {
            log.info("Ingestion failed, will retry (attempt {})", state.getRetryCount());
            // Schedule retry - for now, just reset to allow re-trigger
            state.transitionTo(ProcessingPhase.IDLE);
        }
        processingStateRepository.save(state);
    }

    private void cleanupForReprocessing(AttachmentVideoUnit unit, boolean deleteTranscription) {
        if (deleteTranscription) {
            // First, cancel any running transcription on Nebula
            // The cancel method also deletes the local transcription record
            cancelTranscriptionOnNebula(unit.getId());

            // Fallback: delete local transcription if it still exists
            // (e.g., if cancel failed or transcription was already complete)
            transcriptionRepository.findByLectureUnit_Id(unit.getId()).ifPresent(transcription -> {
                log.info("Deleting existing transcription for unit {}", unit.getId());
                transcriptionRepository.delete(transcription);
            });
        }

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
