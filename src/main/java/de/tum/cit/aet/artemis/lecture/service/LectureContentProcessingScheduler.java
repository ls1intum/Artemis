package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Scheduler for managing lecture content processing jobs.
 * <p>
 * Handles two types of recovery:
 * <ol>
 * <li>Stuck states: Processing that never received a callback (timeout-based)</li>
 * <li>Failed states: Processing that failed and needs retry with exponential backoff</li>
 * </ol>
 * <p>
 * Exponential backoff formula: 2^retryCount minutes (2, 4, 8, 16, 32 minutes for retries 1-5).
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

    /**
     * Maximum number of unprocessed units to pick up per backfill run.
     * Keeps load on Nebula/Pyris manageable.
     */
    private static final int BACKFILL_BATCH_SIZE = 10;

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final LectureContentProcessingService processingService;

    public LectureContentProcessingScheduler(LectureUnitProcessingStateRepository processingStateRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            LectureContentProcessingService processingService) {
        this.processingStateRepository = processingStateRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.processingService = processingService;
    }

    /**
     * Periodically check for processing states that need attention.
     * <p>
     * Handles two scenarios:
     * <ul>
     * <li>Stuck states: Jobs that never received a callback (timeout-based) - increments retry count</li>
     * <li>Failed states: Jobs that failed explicitly and are waiting for retry (backoff-based)</li>
     * </ul>
     * <p>
     * IMPORTANT: Stuck detection runs FIRST to increment retry count before backoff retry kicks in.
     * This ensures stuck retries eventually fail after max retries instead of looping forever.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void processScheduledRetries() {
        log.debug("Checking for processing states that need attention...");

        // First, handle stuck states where callback was never received (increments retry count)
        recoverStuckPhase(ProcessingPhase.TRANSCRIBING, TRANSCRIPTION_TIMEOUT_MINUTES);
        recoverStuckPhase(ProcessingPhase.INGESTING, INGESTION_TIMEOUT_MINUTES);

        // Then, handle states that are ready for retry (either failed explicitly or marked by stuck recovery)
        retryFailedStates(ProcessingPhase.TRANSCRIBING);
        retryFailedStates(ProcessingPhase.INGESTING);
    }

    /**
     * Find and retry all failed states in a specific phase that have waited long enough.
     * Uses exponential backoff: 2^retryCount minutes.
     *
     * @param phase the processing phase to check
     */
    private void retryFailedStates(ProcessingPhase phase) {
        // Find states ready for retry at each backoff level
        for (int retryCount = 1; retryCount < MAX_PROCESSING_RETRIES; retryCount++) {
            long backoffMinutes = LectureContentProcessingService.calculateBackoffMinutes(retryCount);
            ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(backoffMinutes);

            List<LectureUnitProcessingState> readyStates = processingStateRepository.findStatesReadyForRetry(phase, cutoff);

            // Filter to only states at this exact retry count (to avoid double-processing)
            final int currentRetryCount = retryCount;
            List<LectureUnitProcessingState> statesToRetry = readyStates.stream().filter(s -> s.getRetryCount() == currentRetryCount).toList();

            for (LectureUnitProcessingState state : statesToRetry) {
                retryState(state, phase);
            }
        }
    }

    /**
     * Retry a single failed state.
     * Re-fetches state from DB to avoid overwriting concurrent user changes.
     *
     * @param state the state to retry (used only for ID lookup)
     * @param phase the expected processing phase
     */
    private void retryState(LectureUnitProcessingState state, ProcessingPhase phase) {
        // Re-fetch fresh state to avoid overwriting concurrent changes (e.g., user updated content)
        LectureUnitProcessingState freshState = processingStateRepository.findById(state.getId()).orElse(null);
        if (freshState == null) {
            log.debug("State {} no longer exists, skipping retry", state.getId());
            return;
        }

        if (freshState.getPhase() != phase) {
            log.info("State for unit {} changed from {} to {} since batch read, skipping retry", freshState.getLectureUnit().getId(), phase, freshState.getPhase());
            return;
        }

        if (freshState.getLectureUnit() == null) {
            log.warn("Cannot retry state {} - no associated lecture unit", freshState.getId());
            processingStateRepository.delete(freshState);
            return;
        }

        log.info("Retrying {} for unit {} after exponential backoff (attempt {}/{})", phase, freshState.getLectureUnit().getId(), freshState.getRetryCount(),
                MAX_PROCESSING_RETRIES);

        try {
            if (phase == ProcessingPhase.TRANSCRIBING) {
                processingService.retryTranscription(freshState);
            }
            else if (phase == ProcessingPhase.INGESTING) {
                processingService.retryIngestion(freshState);
            }
        }
        catch (Exception e) {
            log.error("Failed to retry {} for unit {}: {}", phase, freshState.getLectureUnit().getId(), e.getMessage());
        }
    }

    /**
     * Find and recover all states stuck in a specific phase (no callback received).
     * Only applies to states that haven't failed yet (retryCount == 0).
     *
     * @param phase          the processing phase to check
     * @param timeoutMinutes the timeout threshold in minutes
     */
    private void recoverStuckPhase(ProcessingPhase phase, int timeoutMinutes) {
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(timeoutMinutes);

        List<LectureUnitProcessingState> stuckStates = processingStateRepository.findStuckStates(List.of(phase), cutoff);

        if (!stuckStates.isEmpty()) {
            log.info("Found {} stuck processing states in phase {} older than {} minutes", stuckStates.size(), phase, timeoutMinutes);

            for (LectureUnitProcessingState state : stuckStates) {
                recoverStuckState(state, phase);
            }
        }
    }

    /**
     * Recover a single stuck processing state.
     * Re-fetches state from DB to avoid overwriting concurrent user changes.
     * Increments retry count and updates startedAt to prevent re-detection.
     * The retryFailedStates method will handle the actual retry with backoff.
     *
     * @param state the stuck processing state to recover (used only for ID lookup)
     * @param phase the expected processing phase
     */
    private void recoverStuckState(LectureUnitProcessingState state, ProcessingPhase phase) {
        // Re-fetch fresh state to avoid overwriting concurrent changes (e.g., user updated content)
        LectureUnitProcessingState freshState = processingStateRepository.findById(state.getId()).orElse(null);
        if (freshState == null) {
            log.debug("State {} no longer exists, skipping recovery", state.getId());
            return;
        }

        if (freshState.getPhase() != phase) {
            log.info("State for unit {} changed from {} to {} since batch read, skipping recovery", freshState.getLectureUnit().getId(), phase, freshState.getPhase());
            return;
        }

        if (freshState.getLectureUnit() == null) {
            log.warn("Cannot recover state {} - no associated lecture unit", freshState.getId());
            processingStateRepository.delete(freshState);
            return;
        }

        log.info("Recovering stuck processing state for unit {}, phase: {}", freshState.getLectureUnit().getId(), phase);

        // Increment retry count - this marks it for retry with backoff
        freshState.incrementRetryCount();
        // Update startedAt to prevent this state from being detected as stuck again
        freshState.setStartedAt(java.time.ZonedDateTime.now());

        if (freshState.getRetryCount() >= MAX_PROCESSING_RETRIES) {
            log.warn("Max recovery attempts reached for unit {}, marking as failed", freshState.getLectureUnit().getId());
            freshState.markFailed("artemisApp.lectureUnit.processing.error.timeout");
        }
        else {
            // retryFailedStates will pick this up after backoff period
            log.info("Stuck state for unit {} marked for retry (attempt {}/{})", freshState.getLectureUnit().getId(), freshState.getRetryCount(), MAX_PROCESSING_RETRIES);
        }

        processingStateRepository.save(freshState);
    }

    /**
     * Periodically process legacy AttachmentVideoUnits that don't have a processing state yet.
     * This handles units that existed before the automated processing pipeline was deployed.
     * <p>
     * Only processes units from active, non-test courses to avoid unnecessary work.
     * Limited to {@link #BACKFILL_BATCH_SIZE} units per run to avoid overwhelming external services.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void backfillUnprocessedUnits() {
        log.debug("Checking for unprocessed lecture units to backfill...");

        List<AttachmentVideoUnit> unprocessedUnits = attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(ZonedDateTime.now(),
                PageRequest.of(0, BACKFILL_BATCH_SIZE));

        if (unprocessedUnits.isEmpty()) {
            log.debug("No unprocessed units found for backfill");
            return;
        }

        log.info("Found {} unprocessed lecture units to backfill", unprocessedUnits.size());

        for (AttachmentVideoUnit unit : unprocessedUnits) {
            try {
                log.info("Triggering processing for legacy unit {} (lecture: {}, course: {})", unit.getId(), unit.getLecture() != null ? unit.getLecture().getId() : "unknown",
                        unit.getLecture() != null && unit.getLecture().getCourse() != null ? unit.getLecture().getCourse().getId() : "unknown");
                processingService.triggerProcessing(unit);
            }
            catch (Exception e) {
                log.error("Failed to trigger processing for unit {}: {}", unit.getId(), e.getMessage());
            }
        }
    }
}
