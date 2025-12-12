package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Scheduler for managing lecture content processing jobs.
 * <p>
 * Handles recovery of stuck processing states after node restart or timeout.
 * States that are stuck longer than their phase-specific timeout are reset
 * and re-triggered for processing.
 * <p>
 * Note: Cleanup of orphaned states (where lecture unit was deleted) is handled
 * automatically by database CASCADE DELETE on the foreign key constraint.
 */
@Component
@Lazy
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class LectureContentProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(LectureContentProcessingScheduler.class);

    /**
     * Timeout in minutes for transcription phase.
     * Transcription can take a while for long videos.
     */
    private static final int TRANSCRIPTION_TIMEOUT_MINUTES = 120; // 2 hours

    /**
     * Timeout in minutes for ingestion phase.
     * Ingestion is generally faster than transcription.
     */
    private static final int INGESTION_TIMEOUT_MINUTES = 60; // 1 hour

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureContentProcessingService processingService;

    public LectureContentProcessingScheduler(LectureUnitProcessingStateRepository processingStateRepository, LectureContentProcessingService processingService) {
        this.processingStateRepository = processingStateRepository;
        this.processingService = processingService;
    }

    /**
     * Periodically check for stuck processing states and recover them.
     * <p>
     * A state is considered stuck if it has been in a processing phase
     * longer than the phase-specific timeout without completing.
     * This can happen if:
     * <ul>
     * <li>The processing node crashed or restarted</li>
     * <li>A callback from Nebula or Pyris was lost</li>
     * <li>The external service is unresponsive</li>
     * </ul>
     * <p>
     * Stuck states are reset to IDLE and re-triggered, up to MAX_PROCESSING_RETRIES times.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void recoverStuckProcessingStates() {
        log.debug("Checking for stuck processing states...");

        recoverPhase(ProcessingPhase.TRANSCRIBING, TRANSCRIPTION_TIMEOUT_MINUTES);
        recoverPhase(ProcessingPhase.INGESTING, INGESTION_TIMEOUT_MINUTES);
    }

    /**
     * Find and recover all states stuck in a specific phase.
     * <p>
     * Queries for states that have been in the given phase longer than
     * the timeout threshold and attempts to recover each one.
     *
     * @param phase          the processing phase to check
     * @param timeoutMinutes the timeout threshold in minutes
     */
    private void recoverPhase(ProcessingPhase phase, int timeoutMinutes) {
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(timeoutMinutes);

        List<LectureUnitProcessingState> stuckStates = processingStateRepository.findStuckStates(List.of(phase), cutoff);

        if (!stuckStates.isEmpty()) {
            log.info("Found {} stuck processing states in phase {} older than {} minutes", stuckStates.size(), phase, timeoutMinutes);

            for (LectureUnitProcessingState state : stuckStates) {
                recoverStuckState(state);
            }
        }
    }

    /**
     * Attempt to recover a single stuck processing state.
     * <p>
     * The state is reset to IDLE and re-triggered for processing.
     * If the retry count exceeds MAX_PROCESSING_RETRIES, the state is marked as failed.
     * <p>
     * Only AttachmentVideoUnit instances can be re-triggered since only they
     * support the automated processing pipeline.
     *
     * @param state the stuck processing state to recover
     */
    private void recoverStuckState(LectureUnitProcessingState state) {
        if (state.getLectureUnit() == null) {
            // This shouldn't happen with CASCADE DELETE, but handle defensively
            log.warn("Cannot recover state {} - no associated lecture unit", state.getId());
            processingStateRepository.delete(state);
            return;
        }

        log.info("Recovering stuck processing state for unit {}, phase: {}", state.getLectureUnit().getId(), state.getPhase());

        state.incrementRetryCount();

        if (state.getRetryCount() >= MAX_PROCESSING_RETRIES) {
            log.warn("Max recovery attempts reached for unit {}, marking as failed", state.getLectureUnit().getId());
            state.markFailed("artemisApp.lectureUnit.processing.error.timeout");
            processingStateRepository.save(state);
            return;
        }

        // Reset to IDLE to allow re-processing
        state.transitionTo(ProcessingPhase.IDLE);
        processingStateRepository.save(state);

        // Trigger re-processing (only AttachmentVideoUnits support automated processing)
        if (state.getLectureUnit() instanceof AttachmentVideoUnit attachmentUnit) {
            try {
                processingService.triggerProcessing(attachmentUnit);
            }
            catch (Exception e) {
                log.error("Failed to re-trigger processing for unit {}: {}", state.getLectureUnit().getId(), e.getMessage());
            }
        }
    }
}
