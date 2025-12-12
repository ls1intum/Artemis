package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;
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
 * <p>
 * Uses a state machine pattern to manage processing phases:
 * <ul>
 * <li>IDLE: Initial state, not processing</li>
 * <li>TRANSCRIBING: Transcription in progress with Nebula</li>
 * <li>INGESTING: Ingestion in progress with Pyris</li>
 * <li>DONE: Processing completed successfully</li>
 * <li>FAILED: Processing failed after max retries</li>
 * </ul>
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class LectureContentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LectureContentProcessingService.class);

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

    // -------------------- Public API --------------------

    /**
     * Main entry point: Trigger processing for an AttachmentVideoUnit.
     * Called when a unit is created or updated.
     * <p>
     * Processing behavior depends on available services:
     * <ul>
     * <li>Nebula ON, Iris ON: Full pipeline (transcription + ingestion)</li>
     * <li>Nebula ON, Iris OFF: Transcription only</li>
     * <li>Nebula OFF, Iris ON: PDF ingestion only</li>
     * <li>Neither available: Skip processing</li>
     * </ul>
     *
     * @param unit the attachment video unit to process
     */
    public void triggerProcessing(AttachmentVideoUnit unit) {
        if (unit == null || unit.getId() == null) {
            log.warn("Cannot process null or unsaved lecture unit");
            return;
        }

        // Skip tutorial lectures
        if (unit.getLecture() != null && unit.getLecture().isTutorialLecture()) {
            log.debug("Skipping processing for tutorial lecture unit: {}", unit.getId());
            return;
        }

        // Check content availability
        boolean hasVideo = unit.getVideoSource() != null && !unit.getVideoSource().isBlank();
        boolean hasPdf = unit.getAttachment() != null && unit.getAttachment().getLink() != null && unit.getAttachment().getLink().endsWith(".pdf");

        if (!hasVideo && !hasPdf) {
            log.debug("Unit {} has no video or PDF to process", unit.getId());
            return;
        }

        // Check service availability
        boolean canTranscribe = hasVideo && transcriptionApi.isPresent() && tumLiveApi.isPresent();
        boolean canIngest = irisLectureApi.isPresent() && (hasPdf || canTranscribe);

        if (!canTranscribe && !canIngest) {
            log.debug("No processing services available for unit {}", unit.getId());
            return;
        }

        // Get or create processing state
        LectureUnitProcessingState state = processingStateRepository.findByLectureUnit_Id(unit.getId()).orElseGet(() -> {
            LectureUnitProcessingState newState = new LectureUnitProcessingState(unit);
            return processingStateRepository.save(newState);
        });

        // Detect and handle content changes
        if (handleContentChanges(unit, state, hasVideo, hasPdf)) {
            state.resetRetryCount();
        }

        // Check if already processing or done
        if (state.isProcessing()) {
            log.debug("Unit {} already processing, skipping", unit.getId());
            return;
        }

        if (state.getPhase() == ProcessingPhase.DONE) {
            log.debug("Unit {} already done, skipping", unit.getId());
            return;
        }

        // Start the state machine
        advanceProcessing(unit, state, hasVideo, hasPdf);
    }

    /**
     * Cancel any ongoing processing for a lecture unit.
     *
     * @param lectureUnitId the ID of the lecture unit
     */
    public void cancelProcessing(Long lectureUnitId) {
        processingStateRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(state -> {
            log.info("Cancelling processing for unit {}", lectureUnitId);

            if (state.getPhase() == ProcessingPhase.TRANSCRIBING) {
                cancelTranscriptionOnNebula(lectureUnitId);
            }

            if (state.getPhase() == ProcessingPhase.INGESTING) {
                cancelIngestionOnPyris(lectureUnitId);
            }

            state.transitionTo(ProcessingPhase.IDLE);
            state.setErrorKey("artemisApp.lectureUnit.processing.cancelled");
            processingStateRepository.save(state);
        });
    }

    /**
     * Called when a transcription completes (from the polling scheduler).
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
            if (state.getPhase() != ProcessingPhase.TRANSCRIBING) {
                return;
            }

            // Race condition check: verify this is the current transcription
            Optional<LectureTranscription> currentTranscription = transcriptionRepository.findByLectureUnit_Id(unitId);
            if (currentTranscription.isEmpty() || !currentTranscription.get().getId().equals(transcription.getId())) {
                log.warn("Ignoring stale transcription callback for unit {}", unitId);
                return;
            }

            if (transcription.getTranscriptionStatus() == TranscriptionStatus.COMPLETED) {
                log.info("Transcription completed for unit {}", unitId);
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
            log.warn("Received ingestion callback for unit {} but no processing state exists", lectureUnitId);
            return;
        }

        LectureUnitProcessingState state = stateOpt.get();
        if (state.getPhase() != ProcessingPhase.INGESTING) {
            log.warn("Received ingestion callback for unit {} in phase {} (expected INGESTING)", lectureUnitId, state.getPhase());
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

    // -------------------- State Machine --------------------

    /**
     * Advance the processing state machine based on current phase.
     */
    private void advanceProcessing(AttachmentVideoUnit unit, LectureUnitProcessingState state, boolean hasVideo, boolean hasPdf) {
        switch (state.getPhase()) {
            case IDLE, FAILED -> startProcessingFromIdle(unit, state, hasVideo, hasPdf);
            case TRANSCRIBING, INGESTING -> {
                // Wait for callbacks
            }
            case DONE -> {
                // Already done
            }
        }
    }

    /**
     * Start processing from IDLE state.
     * Checks for playlist availability inline and transitions directly to TRANSCRIBING or INGESTING.
     */
    private void startProcessingFromIdle(AttachmentVideoUnit unit, LectureUnitProcessingState state, boolean hasVideo, boolean hasPdf) {
        log.info("Starting processing for unit {}", unit.getId());

        if (hasVideo && transcriptionApi.isPresent() && tumLiveApi.isPresent()) {
            // Try to get playlist URL and start transcription
            Optional<String> playlistUrl = fetchPlaylistUrl(unit);

            if (playlistUrl.isPresent()) {
                startTranscription(unit, state, playlistUrl.get());
                return;
            }
            else {
                log.debug("No playlist URL available for unit {}", unit.getId());
            }
        }

        // No transcription possible - try PDF ingestion
        if (hasPdf && irisLectureApi.isPresent()) {
            startIngestion(state);
        }
        else {
            // Nothing to do
            log.debug("No processing possible for unit {} (no playlist and no PDF)", unit.getId());
        }
    }

    // -------------------- Phase Handlers --------------------

    private void startTranscription(AttachmentVideoUnit unit, LectureUnitProcessingState state, String playlistUrl) {
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

    private void startIngestion(LectureUnitProcessingState state) {
        if (irisLectureApi.isEmpty()) {
            log.debug("Iris API not available, skipping ingestion for unit {}", state.getLectureUnit().getId());
            state.transitionTo(ProcessingPhase.IDLE);
            processingStateRepository.save(state);
            return;
        }

        LectureUnit unit = state.getLectureUnit();
        if (!(unit instanceof AttachmentVideoUnit attachmentUnit)) {
            log.warn("Cannot ingest non-AttachmentVideoUnit");
            state.markFailed("artemisApp.lectureUnit.processing.error.invalidUnitType");
            processingStateRepository.save(state);
            return;
        }

        try {
            state.transitionTo(ProcessingPhase.INGESTING);
            processingStateRepository.save(state);

            String result = irisLectureApi.get().addLectureUnitToPyrisDB(attachmentUnit);

            if (result != null) {
                log.info("Ingestion started for unit {}", unit.getId());
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

    // -------------------- Failure Handlers --------------------

    private void handleTranscriptionFailure(LectureUnitProcessingState state) {
        state.incrementRetryCount();

        if (state.getRetryCount() >= MAX_PROCESSING_RETRIES) {
            // Try to fall back to PDF-only ingestion
            LectureUnit unit = state.getLectureUnit();
            boolean hasPdf = unit instanceof AttachmentVideoUnit attachmentUnit && attachmentUnit.getAttachment() != null && attachmentUnit.getAttachment().getLink() != null
                    && attachmentUnit.getAttachment().getLink().endsWith(".pdf");

            if (hasPdf && irisLectureApi.isPresent()) {
                log.info("Max transcription retries reached, falling back to PDF-only for unit {}", unit.getId());
                state.resetRetryCount();
                startIngestion(state);
            }
            else {
                log.warn("Max transcription retries reached, marking as failed for unit {}", unit.getId());
                state.markFailed("artemisApp.lectureUnit.processing.error.transcriptionFailed");
                processingStateRepository.save(state);
            }
        }
        else {
            // Retry by fetching playlist URL again
            retryTranscription(state);
        }
    }

    private void retryTranscription(LectureUnitProcessingState state) {
        LectureUnit unit = state.getLectureUnit();
        if (!(unit instanceof AttachmentVideoUnit attachmentUnit)) {
            state.markFailed("artemisApp.lectureUnit.processing.error.invalidUnitType");
            processingStateRepository.save(state);
            return;
        }

        // Refetch playlist URL
        Optional<String> playlistUrl = fetchPlaylistUrl(attachmentUnit);

        if (playlistUrl.isPresent()) {
            log.info("Retrying transcription for unit {} (attempt {})", unit.getId(), state.getRetryCount());
            processingStateRepository.save(state);
            startTranscription(attachmentUnit, state, playlistUrl.get());
        }
        else {
            // No playlist available anymore - try PDF fallback
            boolean hasPdf = attachmentUnit.getAttachment() != null && attachmentUnit.getAttachment().getLink() != null;
            if (hasPdf && irisLectureApi.isPresent()) {
                log.info("Playlist no longer available, falling back to PDF-only");
                startIngestion(state);
            }
            else {
                state.markFailed("artemisApp.lectureUnit.processing.error.noPlaylist");
                processingStateRepository.save(state);
            }
        }
    }

    private void handleIngestionFailure(LectureUnitProcessingState state) {
        state.incrementRetryCount();

        if (state.getRetryCount() >= MAX_PROCESSING_RETRIES) {
            log.warn("Max ingestion retries reached, marking as failed");
            state.markFailed("artemisApp.lectureUnit.processing.error.ingestionFailed");
            processingStateRepository.save(state);
        }
        else {
            log.info("Retrying ingestion for unit {} (attempt {})", state.getLectureUnit().getId(), state.getRetryCount());
            processingStateRepository.save(state);
            startIngestion(state);
        }
    }

    // -------------------- Helper Methods --------------------

    /**
     * Fetch playlist URL from TUM Live API.
     */
    private Optional<String> fetchPlaylistUrl(AttachmentVideoUnit unit) {
        if (tumLiveApi.isEmpty()) {
            return Optional.empty();
        }

        try {
            return tumLiveApi.get().getTumLivePlaylistLink(unit.getVideoSource());
        }
        catch (Exception e) {
            log.error("Failed to fetch playlist URL for unit {}: {}", unit.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Detect content changes and perform cleanup if needed.
     *
     * @return true if content changed
     */
    private boolean handleContentChanges(AttachmentVideoUnit unit, LectureUnitProcessingState state, boolean hasVideo, boolean hasPdf) {
        String currentVideoHash = computeHash(unit.getVideoSource());
        Integer currentAttachmentVersion = unit.getAttachment() != null ? unit.getAttachment().getVersion() : null;

        boolean videoChanged = hasVideo && !currentVideoHash.equals(state.getVideoSourceHash());
        boolean attachmentChanged = hasPdf && (state.getAttachmentVersion() == null || !state.getAttachmentVersion().equals(currentAttachmentVersion));

        if (videoChanged || attachmentChanged) {
            log.info("Content changed for unit {}, video: {}, attachment: {}", unit.getId(), videoChanged, attachmentChanged);

            if (videoChanged && hasVideo) {
                cleanupForReprocessing(unit, true);
                state.setVideoSourceHash(currentVideoHash);
            }

            if (attachmentChanged && !videoChanged) {
                cleanupForReprocessing(unit, false);
            }

            state.setAttachmentVersion(currentAttachmentVersion);
            return true;
        }

        return false;
    }

    private void cleanupForReprocessing(AttachmentVideoUnit unit, boolean deleteTranscription) {
        if (deleteTranscription) {
            transcriptionRepository.findByLectureUnit_Id(unit.getId()).ifPresent(transcription -> {
                log.info("Deleting existing transcription for unit {}", unit.getId());
                transcriptionRepository.delete(transcription);
            });
            cancelTranscriptionOnNebula(unit.getId());
        }

        cancelIngestionOnPyris(unit.getId());

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

    private void cancelTranscriptionOnNebula(Long lectureUnitId) {
        if (transcriptionApi.isEmpty()) {
            return;
        }

        try {
            transcriptionApi.get().cancelNebulaTranscription(lectureUnitId);
            log.info("Cancelled transcription on Nebula for unit {}", lectureUnitId);
        }
        catch (Exception e) {
            log.warn("Failed to cancel transcription on Nebula for unit {}: {}", lectureUnitId, e.getMessage());
        }
    }

    private void cancelIngestionOnPyris(Long lectureUnitId) {
        if (irisLectureApi.isEmpty()) {
            return;
        }

        try {
            boolean cancelled = irisLectureApi.get().cancelPendingIngestion(lectureUnitId);
            if (cancelled) {
                log.info("Cancelled pending ingestion on Pyris for unit {}", lectureUnitId);
            }
        }
        catch (Exception e) {
            log.warn("Failed to cancel ingestion on Pyris for unit {}: {}", lectureUnitId, e.getMessage());
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
            return String.valueOf(value.hashCode());
        }
    }
}
