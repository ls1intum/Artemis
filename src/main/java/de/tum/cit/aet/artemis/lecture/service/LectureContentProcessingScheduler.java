package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
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
 * <p>
 * Runs only on the scheduling node: multiple nodes running stuck recovery, backfill, and the
 * dispatch tick concurrently would each apply the concurrency cap independently. Direct dispatch
 * from triggers and callbacks still happens on any node; row claiming stays safe everywhere via
 * {@code FOR UPDATE SKIP LOCKED}.
 */
@Conditional(LectureWithIrisEnabled.class)
@Profile(PROFILE_SCHEDULING)
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

    /**
     * How long a worker lease may go unrenewed before the run counts as lost. The worker renews on a
     * fixed 5-second timer that is decoupled from pipeline progress, so unlike
     * {@link #NO_CALLBACK_TIMEOUT_MINUTES} this threshold describes a sleep loop, never the
     * unpredictable duration of an AI stage: a lapse is a strong infrastructure signal (worker
     * process dead or partitioned). Recovery through this path therefore preserves the retry budget.
     * Sized like the Kubernetes node-lease grace: several missed intervals, so one dropped request
     * never reclaims a healthy run. Effective detection latency adds the scan interval of
     * {@link #processScheduledRetries}.
     */
    private static final Duration LEASE_EXPIRY = Duration.ofSeconds(30);

    /**
     * Absolute upper bound in hours for a single ingestion run, regardless of heartbeats.
     * A pipeline that keeps sending heartbeats without ever terminating would otherwise never
     * time out, because every heartbeat resets {@code lastUpdated}. Twelve hours is far beyond
     * any legitimate run (transcribing and ingesting the longest permitted videos), so hitting
     * it reliably indicates a wedged job.
     */
    private static final int ABSOLUTE_TIMEOUT_HOURS = 12;

    /**
     * Age after which a dispatch claim (IDLE row with {@code startedAt} set) whose send never
     * happened is released back into the queue. Ten minutes is far beyond any legitimate gap
     * between the claim commit and the webhook send.
     */
    private static final int CLAIM_EXPIRY_MINUTES = 10;

    /**
     * How long the stage progress counter may stand still, while heartbeats keep arriving, before
     * the run counts as stalled (wedged mid-stage) rather than slow. Configurable via
     * {@code artemis.iris.ingestion.stall-window}.
     */
    private final Duration stallWindow;

    /**
     * How long a run may sit in one stage before a slow-stage warning is logged. Purely
     * observational: a slow run whose progress keeps moving is never killed. Configurable via
     * {@code artemis.iris.ingestion.slow-stage-warning-after}.
     */
    private final Duration slowStageWarningAfter;

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final LectureContentProcessingService processingService;

    private final ProcessingStateCallbackService callbackService;

    private final LectureIngestionReconcileService reconcileService;

    private final ProcessingStateRecoveryService recoveryService;

    private final FeatureToggleService featureToggleService;

    public LectureContentProcessingScheduler(LectureUnitProcessingStateRepository processingStateRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            LectureContentProcessingService processingService, ProcessingStateCallbackService callbackService, LectureIngestionReconcileService reconcileService,
            ProcessingStateRecoveryService recoveryService, FeatureToggleService featureToggleService, @Value("${artemis.iris.ingestion.stall-window:30m}") Duration stallWindow,
            @Value("${artemis.iris.ingestion.slow-stage-warning-after:45m}") Duration slowStageWarningAfter) {
        this.processingStateRepository = processingStateRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.processingService = processingService;
        this.callbackService = callbackService;
        this.reconcileService = reconcileService;
        this.recoveryService = recoveryService;
        this.featureToggleService = featureToggleService;
        this.stallWindow = stallWindow;
        this.slowStageWarningAfter = slowStageWarningAfter;
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
     * <p>
     * This interval is also the granularity of the retry backoff: a unit whose {@code retryEligibleAt}
     * has passed waits here until the next pass, so the scan interval is added to the backoff whenever
     * no other job completes in the meantime. Keep it well below the smallest backoff step (2 minutes)
     * so it does not dominate the wait. {@code fixedDelay} rather than {@code fixedRate}: the pass does
     * real work, and overlapping passes would contend on the same claim rows for nothing.
     */
    @Scheduled(fixedDelayString = "${artemis.iris.ingestion.retry-scan.interval:PT1M}")
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

        // Release dispatch claims whose send never completed (crash between claim commit and webhook)
        int releasedClaims = processingStateRepository.releaseExpiredDispatchClaims(ZonedDateTime.now().minusMinutes(CLAIM_EXPIRY_MINUTES));
        if (releasedClaims > 0) {
            log.warn("dispatch-claim-expired released={} — claims older than {} minutes were requeued", releasedClaims, CLAIM_EXPIRY_MINUTES);
        }

        // Lease reaper: reclaim runs whose worker stopped renewing its lease. A lapsed lease means
        // the worker process is gone (crash, restart, partition), so the retry budget is preserved.
        reclaimLapsedLeases();

        // Stage-level liveness: kill stalled runs (heartbeats without progress), warn about slow ones
        detectStalledAndSlowRuns();

        // Then, handle stuck states where no callback was received recently
        recoverStuckPhase(ProcessingPhase.TRANSCRIBING, NO_CALLBACK_TIMEOUT_MINUTES);
        recoverStuckPhase(ProcessingPhase.INGESTING, NO_CALLBACK_TIMEOUT_MINUTES);

        // Then release dispatch claims whose owner never finished dispatching them, e.g. a node killed by a rolling
        // deploy between taking the claim and writing the phase. Nothing else selects those rows, so without this the
        // unit waits forever; see reclaimLapsedLeases.
        releaseAbandonedDispatchClaims();

        // Then, dispatch any IDLE jobs waiting in the queue (backup trigger)
        callbackService.dispatchPendingJobs();
    }

    /**
     * Reclaim in-flight runs whose worker lease has lapsed: reset them to IDLE for re-dispatch
     * without touching the retry budget. Re-fetches each row and re-checks the lease under current
     * time so a heartbeat that arrived since the batch read cancels the reclaim.
     */
    private void reclaimLapsedLeases() {
        ZonedDateTime cutoff = ZonedDateTime.now().minus(LEASE_EXPIRY);
        List<LectureUnitProcessingState> lapsed = processingStateRepository.findRunsWithLapsedLease(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING), cutoff);
        for (LectureUnitProcessingState candidate : lapsed) {
            LectureUnitProcessingState freshState = processingStateRepository.findById(candidate.getId()).orElse(null);
            boolean stillLapsed = freshState != null && freshState.isProcessing() && freshState.getRetryEligibleAt() == null && freshState.getLastHeartbeatAt() != null
                    && freshState.getLastHeartbeatAt().isBefore(ZonedDateTime.now().minus(LEASE_EXPIRY));
            if (!stillLapsed) {
                continue;
            }
            log.warn("lease-lapsed unit={} locked_by={} last_heartbeat={} — worker stopped renewing, reclaiming the run (retry budget preserved)",
                    freshState.getLectureUnit() != null ? freshState.getLectureUnit().getId() : null, freshState.getLockedBy(), freshState.getLastHeartbeatAt());
            recoveryService.resetToIdleForRecovery(freshState);
        }
    }

    /**
     * Distinguish the three liveness states of an in-flight run:
     * <ul>
     * <li><b>Dead</b> — heartbeats stopped: handled by {@link #recoverStuckPhase} via the no-callback timeout.</li>
     * <li><b>Stalled</b> — the run is still alive (status callbacks still arriving, or a worker lease still
     * held) but the stage progress counter stopped moving for the whole stall window. The run is wedged
     * mid-stage (hung request, blocked IO) and is treated like a stuck run: failed with retry budget,
     * because the content itself may be the cause.</li>
     * <li><b>Slow</b> — the progress counter keeps moving but the stage has been running longer than the
     * warning threshold. Logged for visibility and never killed: a three-hour lecture video legitimately
     * transcribes for a long time.</li>
     * </ul>
     * Only runs that report stage progress can be classified here; runs from older Iris versions fall
     * back to the plain heartbeat timeout.
     */
    private void detectStalledAndSlowRuns() {
        ZonedDateTime now = ZonedDateTime.now();
        List<LectureUnitProcessingState> inFlightStates = processingStateRepository.findByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING));
        for (LectureUnitProcessingState state : inFlightStates) {
            // Classify only runs that have reported a stage: the stall window needs a progress clock to
            // compare against. A run with no progress yet — the whole transcription phase, which sends no
            // stage name — is judged instead by findStuckStates' no-callback arm on lastUpdated (bumped by
            // every raw checkpoint), the right signal for an opaque AI stage the stall window cannot size.
            if (state.getLastProgressAt() == null || state.getRetryEligibleAt() != null) {
                continue;
            }
            // Alive means recent status callbacks OR a still-held lease. renewLease bumps only
            // lastHeartbeatAt, so a run wedged mid-stage under a healthy worker keeps a fresh lease while
            // callbacks fall silent; without the lease arm such a run (having reported a stage) would still
            // escape this detector, findStuckStates and reclaimLapsedLeases alike.
            boolean callbacksRecent = state.getLastUpdated() != null && state.getLastUpdated().isAfter(now.minusMinutes(NO_CALLBACK_TIMEOUT_MINUTES));
            boolean leaseHeld = state.getLastHeartbeatAt() != null && state.getLastHeartbeatAt().isAfter(now.minus(LEASE_EXPIRY));
            boolean heartbeatsAlive = callbacksRecent || leaseHeld;
            boolean progressFrozen = state.getLastProgressAt().isBefore(now.minus(stallWindow));
            if (heartbeatsAlive && progressFrozen) {
                failStalledState(state);
            }
            else if (state.getStageStartedAt() != null && state.getStageStartedAt().isBefore(now.minus(slowStageWarningAfter))) {
                log.warn("slow-stage unit={} stage={} progress={}/{} in_stage_since={} — progress is moving, not intervening", state.getLectureUnit().getId(),
                        state.getCurrentStage(), state.getStageProgress(), state.getStageTotal(), state.getStageStartedAt());
            }
        }
    }

    /**
     * Fail a stalled run, but only after re-reading the row and re-confirming it is still in flight and
     * still stalled. The batch read that found this candidate may be stale: a terminal success callback
     * can land between the read and this write, and because the entity has no optimistic-lock version, an
     * unconditional {@code save} would revert a just-completed unit to FAILED and wipe its confirmed
     * fingerprint. Re-fetching mirrors {@link #recoverStuckState} and closes that race.
     *
     * @param staleState the stalled candidate from the batch read (used only for its id)
     */
    private void failStalledState(LectureUnitProcessingState staleState) {
        LectureUnitProcessingState freshState = processingStateRepository.findById(staleState.getId()).orElse(null);
        if (freshState == null || freshState.getLectureUnit() == null) {
            return;
        }
        // The callback may have finished, failed, or a new run may have restarted the clock since the batch read.
        boolean stillStalled = freshState.isProcessing() && freshState.getRetryEligibleAt() == null && freshState.getLastProgressAt() != null
                && freshState.getLastProgressAt().isBefore(ZonedDateTime.now().minus(stallWindow));
        if (!stillStalled) {
            log.debug("Unit {} no longer stalled since batch read (phase {}), skipping", freshState.getLectureUnit().getId(), freshState.getPhase());
            return;
        }
        log.warn("stalled-progress unit={} stage={} progress={}/{} frozen_since={} — heartbeats alive but no progress, failing the run for retry",
                freshState.getLectureUnit().getId(), freshState.getCurrentStage(), freshState.getStageProgress(), freshState.getStageTotal(), freshState.getLastProgressAt());
        callbackService.handleProcessingFailure(freshState);
    }

    /**
     * Find and recover all states stuck in a specific phase (no callback received).
     *
     * @param phase          the processing phase to check
     * @param timeoutMinutes the timeout threshold in minutes
     */
    private void recoverStuckPhase(ProcessingPhase phase, int timeoutMinutes) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime cutoff = now.minusMinutes(timeoutMinutes);
        ZonedDateTime absoluteCutoff = now.minusHours(ABSOLUTE_TIMEOUT_HOURS);

        List<LectureUnitProcessingState> stuckStates = processingStateRepository.findStuckStates(List.of(phase), cutoff, absoluteCutoff);

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

        // A stuck INGESTING run may have completed with only its terminal callback lost. In that case
        // the census evidence lets us requeue without charging the retry budget, so a series of lost
        // callbacks can never mark a fully ingested unit as permanently failed.
        if (phase == ProcessingPhase.INGESTING && reconcileService.resolveStuckIngestionWithoutRetryPenalty(freshState)) {
            return;
        }

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
     * Limited by the configured maximum number of concurrent jobs to avoid overwhelming external services.
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
        int maxConcurrentJobs = callbackService.getMaxConcurrentJobs();
        long currentlyProcessing = processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING));
        if (currentlyProcessing >= maxConcurrentJobs) {
            log.debug("Already {} units processing (max {}), skipping backfill", currentlyProcessing, maxConcurrentJobs);
            return;
        }

        // Calculate how many more jobs we can start
        int availableSlots = (int) (maxConcurrentJobs - currentlyProcessing);

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
                processingService.triggerProcessingAsBacklog(unit);
            }
            catch (Exception e) {
                log.error("Failed to trigger processing for unit {}: {}", unit.getId(), e.getMessage());
            }
        }
    }

    /**
     * Periodically reconcile the vector index against the database: requeue units whose confirmed state,
     * current content, and index stamp diverge, trigger units that never entered the pipeline, and delete
     * orphaned index rows. Walks a budgeted slice of courses per run, so a full pass over all courses
     * takes several runs and never floods the queue; see {@link LectureIngestionReconcileService}.
     */
    @Scheduled(initialDelayString = "${artemis.iris.ingestion.reconcile.initial-delay:PT15M}", fixedDelayString = "${artemis.iris.ingestion.reconcile.interval:PT1H}")
    public void reconcileIngestionState() {
        if (!featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)) {
            log.debug("LectureContentProcessing feature is disabled, skipping ingestion reconcile");
            return;
        }
        if (!processingService.hasProcessingCapabilities()) {
            log.debug("No processing services available, skipping ingestion reconcile");
            return;
        }

        int spent = reconcileService.walkNextCourses();
        if (spent > 0) {
            log.info("Ingestion reconcile requeued or triggered {} units, dispatching", spent);
            callbackService.dispatchPendingJobs();
        }
        else {
            // Logged even when the pass changes nothing: without this line a healthy reconciler and one that
            // never ran look identical in the log, which is the only place its liveness is observable.
            log.info("Ingestion reconcile pass completed with nothing to requeue");
        }
    }
}
