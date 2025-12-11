package de.tum.cit.aet.artemis.lecture.service;

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
 * Handles:
 * <ul>
 * <li>Recovery after node restart (detecting stuck states)</li>
 * <li>Timeout detection for hung processes</li>
 * <li>Periodic cleanup of stale states</li>
 * </ul>
 */
@Component
@Lazy
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class LectureContentProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(LectureContentProcessingScheduler.class);

    /**
     * Time in minutes after which a processing state is considered stuck.
     * Different phases have different timeout thresholds.
     */
    private static final int CHECKING_PLAYLIST_TIMEOUT_MINUTES = 5;

    private static final int TRANSCRIPTION_TIMEOUT_MINUTES = 120; // 2 hours - transcription can take a while

    private static final int INGESTION_TIMEOUT_MINUTES = 60; // 1 hour

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureContentProcessingService processingService;

    public LectureContentProcessingScheduler(LectureUnitProcessingStateRepository processingStateRepository, LectureContentProcessingService processingService) {
        this.processingStateRepository = processingStateRepository;
        this.processingService = processingService;
    }

    /**
     * Periodically check for stuck processing states and recover them.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void recoverStuckProcessingStates() {
        log.debug("Checking for stuck processing states...");

        recoverPhase(ProcessingPhase.CHECKING_PLAYLIST, CHECKING_PLAYLIST_TIMEOUT_MINUTES);
        recoverPhase(ProcessingPhase.TRANSCRIBING, TRANSCRIPTION_TIMEOUT_MINUTES);
        recoverPhase(ProcessingPhase.INGESTING, INGESTION_TIMEOUT_MINUTES);
    }

    /**
     * Clean up orphaned processing states where the lecture unit no longer exists.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void cleanupOrphanedStates() {
        log.debug("Cleaning up orphaned processing states...");

        List<LectureUnitProcessingState> allStates = processingStateRepository.findAll();
        int cleaned = 0;

        for (LectureUnitProcessingState state : allStates) {
            if (state.getLectureUnit() == null) {
                log.info("Deleting orphaned processing state {}", state.getId());
                processingStateRepository.delete(state);
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned up {} orphaned processing states", cleaned);
        }
    }

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

    private void recoverStuckState(LectureUnitProcessingState state) {
        if (state.getLectureUnit() == null) {
            log.warn("Cannot recover state {} - no associated lecture unit", state.getId());
            processingStateRepository.delete(state);
            return;
        }

        log.info("Recovering stuck processing state for unit {}, phase: {}", state.getLectureUnit().getId(), state.getPhase());

        state.incrementRetryCount();

        if (state.getRetryCount() >= 3) {
            log.warn("Max recovery attempts reached for unit {}, marking as failed", state.getLectureUnit().getId());
            state.markFailed("Processing timed out after multiple recovery attempts");
            processingStateRepository.save(state);
            return;
        }

        // Reset to IDLE to allow re-processing
        state.transitionTo(ProcessingPhase.IDLE);
        processingStateRepository.save(state);

        // Trigger re-processing
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
