package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService.MAX_CONCURRENT_PROCESSING;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
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
@Conditional(LectureWithIrisEnabled.class)
@Component
@Lazy
public class LectureContentProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(LectureContentProcessingScheduler.class);

    /**
     * Timeout in minutes for detecting stuck jobs (no callback received).
     * With Iris heartbeats firing every ~5-10 minutes during Whisper transcription,
     * prolonged silence means the pipeline is dead or lost connectivity.
     * <p>
     * Set to 20 minutes to accommodate single long-running stages without heartbeats
     * (e.g. video download on slow wifi, audio extraction for very large files).
     * A heartbeat fires when each stage starts, so 20 minutes of silence after that
     * reliably indicates a stuck pipeline.
     * <p>
     * Note: this checks {@code lastUpdated}, not {@code startedAt}. Every heartbeat
     * and checkpoint callback resets the clock, so a healthy 2-hour transcription
     * is never considered stuck.
     */
    private static final int NO_CALLBACK_TIMEOUT_MINUTES = 20;

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final LectureContentProcessingService processingService;

    private final ProcessingStateCallbackService callbackService;

    private final FeatureToggleService featureToggleService;

    public LectureContentProcessingScheduler(LectureUnitProcessingStateRepository processingStateRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            LectureContentProcessingService processingService, ProcessingStateCallbackService callbackService, FeatureToggleService featureToggleService) {
        this.processingStateRepository = processingStateRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.processingService = processingService;
        this.callbackService = callbackService;
        this.featureToggleService = featureToggleService;
    }

    /**
     * Periodically check for processing states that need attention and dispatch pending jobs.
     * <p>
     * Handles two scenarios:
     * <ol>
     * <li>Stuck states: Jobs that never received a callback (timeout-based) — reset to IDLE for re-dispatch</li>
     * <li>Dispatch: Claim IDLE jobs and send them to Iris if capacity is available (backup trigger)</li>
     * </ol>
     * <p>
     * The dispatcher is also triggered by job creation and completion callbacks, so this
     * scheduled run serves as a safety net for edge cases (missed callbacks, node restarts).
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void processScheduledRetries() {
        if (!featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)) {
            log.debug("LectureContentProcessing feature is disabled, skipping scheduled retries");
            return;
        }

        if (!processingService.hasProcessingCapabilities()) {
            log.debug("No processing services available, skipping scheduled retries");
            return;
        }

        log.debug("Checking for processing states that need attention...");

        // First, handle stuck states where no callback was received recently
        recoverStuckPhase(ProcessingPhase.TRANSCRIBING, NO_CALLBACK_TIMEOUT_MINUTES);
        recoverStuckPhase(ProcessingPhase.INGESTING, NO_CALLBACK_TIMEOUT_MINUTES);

        // Then, dispatch any IDLE jobs waiting in the queue (backup trigger)
        callbackService.dispatchPendingJobs();
    }

    /**
     * Find and recover all states stuck in a specific phase (no callback received).
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
     * Recover a single stuck processing state by resetting to IDLE for re-dispatch.
     * Re-fetches state from DB to avoid overwriting concurrent user changes.
     *
     * @param state the stuck processing state to recover (used only for ID lookup)
     * @param phase the expected processing phase
     */
    private void recoverStuckState(LectureUnitProcessingState state, ProcessingPhase phase) {
        LectureUnitProcessingState freshState = processingStateRepository.findById(state.getId()).orElse(null);
        if (freshState == null) {
            log.debug("State {} no longer exists, skipping recovery", state.getId());
            return;
        }

        if (freshState.getLectureUnit() == null) {
            log.warn("Cannot recover state {} - no associated lecture unit", freshState.getId());
            processingStateRepository.delete(freshState);
            return;
        }

        if (freshState.getPhase() != phase) {
            log.info("State for unit {} changed from {} to {} since batch read, skipping recovery", freshState.getLectureUnit().getId(), phase, freshState.getPhase());
            return;
        }

        if (freshState.getRetryEligibleAt() != null) {
            log.debug("State {} already scheduled for retry, skipping stuck recovery", freshState.getId());
            return;
        }

        log.info("Recovering stuck processing state for unit {}, phase: {}", freshState.getLectureUnit().getId(), phase);

        // Treat stuck jobs as failures: the content itself may cause Iris to hang or crash
        // silently (e.g. malformed PDF, OOM during transcription). Incrementing retryCount
        // ensures poison-pill jobs eventually fail permanently instead of looping forever.
        callbackService.handleProcessingFailure(freshState);
    }

    /**
     * Periodically process legacy AttachmentVideoUnits that don't have a processing state yet.
     * This handles units that existed before the automated processing pipeline was deployed.
     * <p>
     * Only processes units from active, non-test courses to avoid unnecessary work.
     * Limited by {@link #MAX_CONCURRENT_PROCESSING} to avoid overwhelming external services.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void backfillUnprocessedUnits() {
        if (!featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)) {
            log.debug("LectureContentProcessing feature is disabled, skipping backfill");
            return;
        }

        if (!processingService.hasProcessingCapabilities()) {
            log.debug("No processing services available, skipping backfill");
            return;
        }

        log.debug("Checking for unprocessed lecture units to backfill...");

        // Check how many jobs are currently processing
        long currentlyProcessing = processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING));
        if (currentlyProcessing >= MAX_CONCURRENT_PROCESSING) {
            log.debug("Already {} units processing (max {}), skipping backfill", currentlyProcessing, MAX_CONCURRENT_PROCESSING);
            return;
        }

        // Calculate how many more jobs we can start
        int availableSlots = (int) (MAX_CONCURRENT_PROCESSING - currentlyProcessing);

        List<AttachmentVideoUnit> unprocessedUnits = attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(ZonedDateTime.now(), PageRequest.of(0, availableSlots));

        if (unprocessedUnits.isEmpty()) {
            log.debug("No unprocessed units found for backfill");
            return;
        }

        log.info("Found {} unprocessed lecture units to backfill ({} slots available)", unprocessedUnits.size(), availableSlots);

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
