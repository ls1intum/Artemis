package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisOrNebulaEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Service that handles callbacks and state transitions for the lecture content processing pipeline.
 * <p>
 * This service is extracted from {@link LectureContentProcessingService} to break the circular dependency
 * between the lecture and nebula modules. It handles:
 * <ul>
 * <li>Transcription completion callbacks from {@link de.tum.cit.aet.artemis.nebula.service.LectureTranscriptionService}</li>
 * <li>Ingestion completion callbacks from Pyris webhooks</li>
 * <li>Starting ingestion jobs with Pyris</li>
 * <li>Failure handling and retry logic</li>
 * </ul>
 */
@Conditional(LectureWithIrisOrNebulaEnabled.class)
@Service
@Lazy
public class ProcessingStateCallbackService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingStateCallbackService.class);

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    private final Optional<IrisLectureApi> irisLectureApi;

    public ProcessingStateCallbackService(LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository,
            Optional<IrisLectureApi> irisLectureApi) {
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.irisLectureApi = irisLectureApi;
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
                    state.resetRetryCount(); // Fresh retries for ingestion phase
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
     * Validates the job token to reject stale callbacks from old jobs.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token from the callback
     * @param success       whether ingestion succeeded
     */
    public void handleIngestionComplete(Long lectureUnitId, String jobToken, boolean success) {
        Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByLectureUnit_Id(lectureUnitId);

        if (stateOpt.isEmpty()) {
            log.warn("Received ingestion callback for unit {} but no processing state exists", lectureUnitId);
            return;
        }

        LectureUnitProcessingState state = stateOpt.get();

        // Validate token - reject stale callbacks from old jobs
        if (!Objects.equals(jobToken, state.getIngestionJobToken())) {
            log.info("Ignoring stale ingestion callback for unit {} (token mismatch: expected {}, got {})", lectureUnitId, maskToken(state.getIngestionJobToken()),
                    maskToken(jobToken));
            return;
        }

        if (state.getPhase() != ProcessingPhase.INGESTING) {
            log.warn("Received ingestion callback for unit {} in phase {} (expected INGESTING)", lectureUnitId, state.getPhase());
            return;
        }

        if (success) {
            log.info("Ingestion completed successfully for unit {}", lectureUnitId);
            state.transitionTo(ProcessingPhase.DONE);
            state.setIngestionJobToken(null); // Clear token after successful completion
        }
        else {
            log.warn("Ingestion failed for unit {}", lectureUnitId);
            handleIngestionFailure(state);
        }
        processingStateRepository.save(state);
    }

    /**
     * Starts ingestion for a processing state.
     * Called after transcription completes or for PDF-only units.
     *
     * @param state the processing state to start ingestion for
     */
    public void startIngestion(LectureUnitProcessingState state) {
        if (irisLectureApi.isEmpty()) {
            log.debug("Iris API not available, skipping ingestion for unit {}", state.getLectureUnit().getId());
            state.transitionTo(ProcessingPhase.DONE);
            processingStateRepository.save(state);
            return;
        }

        LectureUnit unit = state.getLectureUnit();
        if (!(unit instanceof AttachmentVideoUnit attachmentUnit)) {
            log.warn("Cannot ingest non-AttachmentVideoUnit");
            state.markFailed("artemisApp.attachmentVideoUnit.processing.error.invalidUnitType");
            processingStateRepository.save(state);
            return;
        }

        try {
            // Call API FIRST, BEFORE transitioning state
            String jobToken = irisLectureApi.get().addLectureUnitToPyrisDB(attachmentUnit);

            if (jobToken == null) {
                // Not applicable (Iris disabled for course, tutorial lecture, etc.) - mark as done
                log.debug("Ingestion not applicable for unit {} (course settings or content type)", unit.getId());
                state.transitionTo(ProcessingPhase.DONE);
                processingStateRepository.save(state);
                return;
            }

            // Job started successfully - now transition to INGESTING
            state.transitionTo(ProcessingPhase.INGESTING);
            state.setIngestionJobToken(jobToken);
            processingStateRepository.save(state);
            log.info("Ingestion started for unit {} with token {}", unit.getId(), maskToken(jobToken));
        }
        catch (Exception e) {
            // Transition to INGESTING so scheduler can retry (state may be in IDLE or TRANSCRIBING)
            state.transitionTo(ProcessingPhase.INGESTING);
            log.error("Failed to start ingestion for unit {}: {}", unit.getId(), e.getMessage());
            handleIngestionFailure(state);
        }
    }

    /**
     * Handles transcription failure with retry logic.
     * If max retries reached, attempts fallback to PDF-only ingestion.
     *
     * @param state the processing state that failed
     */
    public void handleTranscriptionFailure(LectureUnitProcessingState state) {
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
                state.markFailed("artemisApp.attachmentVideoUnit.processing.error.transcriptionFailed");
                processingStateRepository.save(state);
            }
        }
        else {
            // Stay in TRANSCRIBING phase, scheduler will retry after backoff period
            long backoffMinutes = calculateBackoffMinutes(state.getRetryCount());
            state.scheduleRetry(backoffMinutes);
            log.info("Transcription failed for unit {}, scheduled for retry in {} minutes (attempt {}/{})", state.getLectureUnit().getId(), backoffMinutes, state.getRetryCount(),
                    MAX_PROCESSING_RETRIES);
            processingStateRepository.save(state);
        }
    }

    /**
     * Handles ingestion failure with retry logic.
     *
     * @param state the processing state that failed
     */
    public void handleIngestionFailure(LectureUnitProcessingState state) {
        state.incrementRetryCount();

        if (state.getRetryCount() >= MAX_PROCESSING_RETRIES) {
            log.warn("Max ingestion retries reached, marking as failed");
            state.markFailed("artemisApp.attachmentVideoUnit.processing.error.ingestionFailed");
            processingStateRepository.save(state);
        }
        else {
            // Stay in INGESTING phase, scheduler will retry after backoff period
            long backoffMinutes = calculateBackoffMinutes(state.getRetryCount());
            state.scheduleRetry(backoffMinutes);
            log.info("Ingestion failed for unit {}, scheduled for retry in {} minutes (attempt {}/{})", state.getLectureUnit().getId(), backoffMinutes, state.getRetryCount(),
                    MAX_PROCESSING_RETRIES);
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
        log.info("Retrying ingestion for unit {} (attempt {}/{})", state.getLectureUnit().getId(), state.getRetryCount(), MAX_PROCESSING_RETRIES);
        // Clear retry eligibility - we're starting the retry now
        state.clearRetryEligibility();
        // Update timestamps to mark retry start
        state.setStartedAt(ZonedDateTime.now());
        state.setLastUpdated(ZonedDateTime.now());
        processingStateRepository.save(state);
        startIngestion(state);
    }

    /**
     * Calculate exponential backoff delay in minutes.
     * Formula: 2^retryCount minutes (2, 4, 8, 16, 32 minutes for retries 1-5).
     *
     * @param retryCount current retry attempt number
     * @return backoff delay in minutes
     */
    static long calculateBackoffMinutes(int retryCount) {
        return (long) Math.pow(2, retryCount);
    }

    /**
     * Masks a token for safe logging by showing only the first and last 3 characters.
     *
     * @param token the token to mask
     * @return the masked token (e.g., "abc...xyz")
     */
    private String maskToken(String token) {
        if (token == null) {
            return "null";
        }
        int len = token.length();
        if (len <= 6) {
            return "***";
        }
        return token.substring(0, 3) + "..." + token.substring(len - 3);
    }
}
