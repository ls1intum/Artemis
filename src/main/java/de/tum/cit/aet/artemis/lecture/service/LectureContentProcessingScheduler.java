package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

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
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRecoveryRepository;
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
 * Runs only on the scheduling node (one node per installation): stuck recovery, backfill and the
 * reconcile walk keep their state and their bound on background work in memory. Pyris workers claim
 * jobs through any node; claiming stays safe everywhere through conditional claim updates, which only
 * one caller can win.
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
    private final int noCallbackTimeoutMinutes;

    /**
     * How long a worker lease may go unrenewed before the run counts as lost. The worker renews on a
     * fixed 5-second timer that is decoupled from pipeline progress, so unlike
     * {@link #noCallbackTimeoutMinutes} this threshold describes a sleep loop, never the
     * unpredictable duration of an AI stage: a lapse is a strong infrastructure signal (worker
     * process dead or partitioned). Recovery through this path therefore preserves the retry budget.
     * Sized like the Kubernetes node-lease grace: several missed intervals, so one dropped request
     * never reclaims a healthy run. That includes one heartbeat request hanging for the worker's full
     * 30-second request timeout, which a 30-second lease would not survive. Effective detection latency adds the scan interval of
     * {@link #processScheduledRetries}.
     */
    private final Duration leaseExpiry;

    /**
     * Absolute upper bound in hours for a single ingestion run, regardless of heartbeats.
     * A pipeline that keeps sending heartbeats without ever terminating would otherwise never
     * time out, because every heartbeat resets {@code lastUpdated}. Twelve hours is far beyond
     * any legitimate run (transcribing and ingesting the longest permitted videos), so hitting
     * it reliably indicates a wedged job.
     */
    private final int absoluteTimeoutHours;

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

    private final LectureUnitProcessingStateRecoveryRepository recoveryRepository;

    private final FeatureToggleService featureToggleService;

    public LectureContentProcessingScheduler(LectureUnitProcessingStateRepository processingStateRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            LectureContentProcessingService processingService, ProcessingStateCallbackService callbackService, LectureIngestionReconcileService reconcileService,
            ProcessingStateRecoveryService recoveryService, LectureUnitProcessingStateRecoveryRepository recoveryRepository, FeatureToggleService featureToggleService,
            @Value("${artemis.iris.ingestion.stall-window:30m}") Duration stallWindow,
            @Value("${artemis.iris.ingestion.slow-stage-warning-after:45m}") Duration slowStageWarningAfter,
            @Value("${artemis.iris.ingestion.no-callback-timeout-minutes:20}") int noCallbackTimeoutMinutes,
            @Value("${artemis.iris.ingestion.lease-expiry:60s}") Duration leaseExpiry, @Value("${artemis.iris.ingestion.absolute-timeout-hours:12}") int absoluteTimeoutHours) {
        requirePositive(stallWindow, "artemis.iris.ingestion.stall-window");
        requirePositive(slowStageWarningAfter, "artemis.iris.ingestion.slow-stage-warning-after");
        requirePositive(noCallbackTimeoutMinutes, "artemis.iris.ingestion.no-callback-timeout-minutes");
        requirePositive(leaseExpiry, "artemis.iris.ingestion.lease-expiry");
        requirePositive(absoluteTimeoutHours, "artemis.iris.ingestion.absolute-timeout-hours");
        this.processingStateRepository = processingStateRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.processingService = processingService;
        this.callbackService = callbackService;
        this.reconcileService = reconcileService;
        this.recoveryService = recoveryService;
        this.recoveryRepository = recoveryRepository;
        this.featureToggleService = featureToggleService;
        this.stallWindow = stallWindow;
        this.slowStageWarningAfter = slowStageWarningAfter;
        this.noCallbackTimeoutMinutes = noCallbackTimeoutMinutes;
        this.leaseExpiry = leaseExpiry;
        this.absoluteTimeoutHours = absoluteTimeoutHours;
    }

    /**
     * Rejects a non-positive recovery threshold at startup rather than letting it silently make every
     * live run immediately eligible for stall/timeout/lease-reclaim handling once scheduling starts.
     */
    private static void requirePositive(Duration value, String property) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " must be strictly positive, but was " + value);
        }
    }

    private static void requirePositive(int value, String property) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " must be strictly positive, but was " + value);
        }
    }

    /**
     * Periodically check for processing states that need attention: lapsed worker leases, stalled and stuck runs,
     * interrupted content changes, and abandoned claims. Queued jobs need nothing here: Pyris workers claim them,
     * retries included once their backoff has passed.
     * <p>
     * {@code fixedDelay} rather than {@code fixedRate}: the pass does real work, and overlapping passes would contend
     * on the same rows for nothing.
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

        // Lease reaper: reclaim runs whose worker stopped renewing its lease. A lapsed lease means
        // the worker process is gone (crash, restart, partition), so the retry budget is preserved.
        reclaimLapsedLeases();

        // Stage-level liveness: kill stalled runs (heartbeats without progress), warn about slow ones
        detectStalledAndSlowRuns();

        // Then, handle stuck states where no callback was received recently
        recoverStuckPhase(ProcessingPhase.TRANSCRIBING, noCallbackTimeoutMinutes);
        recoverStuckPhase(ProcessingPhase.INGESTING, noCallbackTimeoutMinutes);

        // Then resume content changes interrupted after the run's token was invalidated; nothing above can match them
        resumeInterruptedContentChanges();

        // Then release claims whose activation never happened, e.g. a node killed by a rolling deploy between
        // taking the claim and writing the phase. Nothing else selects those rows, so without this the unit waits
        // forever; see releaseAbandonedIdleClaims.
        releaseAbandonedDispatchClaims();
    }

    /**
     * Resume content changes that stopped between invalidating the in-flight run's token and requeueing the unit, see
     * {@link LectureUnitProcessingStateRecoveryRepository}. Each row is claimed first, atomically re-checking that it is
     * still stranded, so a row that recovered since the batch read, or that another pass already took, is skipped; the
     * recovery then only writes while that claim holds. Each row is isolated like the other recovery loops.
     */
    private void resumeInterruptedContentChanges() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime cutoff = now.minusMinutes(noCallbackTimeoutMinutes);
        for (LectureUnitProcessingState state : recoveryRepository.findStrandedRuns(cutoff)) {
            try {
                if (state.getLectureUnit() == null) {
                    continue;
                }
                String claimToken = UUID.randomUUID().toString();
                if (recoveryRepository.claimStrandedRun(state.getId(), claimToken, cutoff, now) == 0) {
                    continue;
                }
                long unitId = state.getLectureUnit().getId();
                log.warn("interrupted-content-change unit={} phase={} — the run's token was invalidated but the unit was never requeued, resuming the cleanup", unitId,
                        state.getPhase());
                attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(unitId)
                        .ifPresent(unit -> processingService.recoverInterruptedContentChange(unit, claimToken));
            }
            catch (RuntimeException e) {
                log.error("Resuming the interrupted content change of processing state {} failed, skipping it this pass: {}", state.getId(), e.getMessage());
            }
        }
    }

    /**
     * Release dispatch claims that a node abandoned mid-dispatch.
     * <p>
     * The claim commits before the dispatch it belongs to, so a node dying in between leaves a row that no query
     * selects: IDLE with a {@code startedAt} set. The cutoff is the same no-callback timeout used above — a claim
     * older than that is not in flight any more.
     * <p>
     * Retry claims need nothing here: {@code claimRetryEligible} leases {@code retryEligibleAt} into the future
     * instead of clearing it, so an abandoned retry becomes eligible again when the lease lapses.
     */
    private void releaseAbandonedDispatchClaims() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime cutoff = now.minusMinutes(noCallbackTimeoutMinutes);

        int releasedClaims = processingStateRepository.releaseAbandonedIdleClaims(cutoff, now);
        if (releasedClaims > 0) {
            log.info("Released {} abandoned dispatch claims older than {} minutes; the units are back in the queue", releasedClaims, noCallbackTimeoutMinutes);
        }
    }

    /**
     * Reclaim in-flight runs whose worker lease has lapsed: reset them to IDLE for re-dispatch
     * without touching the retry budget. Re-fetches each row and re-checks the lease under current
     * time so a heartbeat that arrived since the batch read cancels the reclaim.
     */
    private void reclaimLapsedLeases() {
        List<ProcessingPhase> inFlightPhases = List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING);
        ZonedDateTime cutoff = ZonedDateTime.now().minus(leaseExpiry);
        List<LectureUnitProcessingState> lapsed = processingStateRepository.findRunsWithLapsedLease(inFlightPhases, cutoff);
        for (LectureUnitProcessingState candidate : lapsed) {
            // Atomic: a heartbeat renewing the lease, or a terminal callback finishing the run, in the window
            // since the batch read above cancels this reclaim instead of being overwritten by it. See
            // LectureUnitProcessingStateRepository#reclaimLapsedLease. Isolated per run, like the stage-liveness check.
            try {
                boolean reclaimed = recoveryService.reclaimLapsedLease(candidate.getId(), candidate.getIngestionJobToken(), inFlightPhases, cutoff);
                if (reclaimed) {
                    log.warn("lease-lapsed unit={} locked_by={} last_heartbeat={} — worker stopped renewing, reclaiming the run (retry budget preserved)",
                            candidate.getLectureUnit() != null ? candidate.getLectureUnit().getId() : null, candidate.getLockedBy(), candidate.getLastHeartbeatAt());
                }
            }
            catch (RuntimeException e) {
                log.error("Lease reclaim failed for processing state {}, skipping it this pass: {}", candidate.getId(), e.getMessage());
            }
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
            // compare against. A run with no progress yet — before its first stage, or from an older Iris that
            // sends no stage names — is judged instead by findStuckStates' no-callback arm on lastUpdated.
            if (state.getLectureUnit() == null || state.getLastProgressAt() == null || state.getRetryEligibleAt() != null) {
                continue;
            }
            // Isolate each run: one that throws must not abort the pass, which would also skip stuck recovery,
            // claim release and dispatch for every other unit, on every pass while the bad row remains.
            try {
                // Alive means recent status callbacks OR a still-held lease. renewLease bumps only
                // lastHeartbeatAt, so a run wedged mid-stage under a healthy worker keeps a fresh lease while
                // callbacks fall silent; without the lease arm such a run (having reported a stage) would still
                // escape this detector, findStuckStates and reclaimLapsedLeases alike.
                boolean callbacksRecent = state.getLastUpdated() != null && state.getLastUpdated().isAfter(now.minusMinutes(noCallbackTimeoutMinutes));
                boolean leaseHeld = state.getLastHeartbeatAt() != null && state.getLastHeartbeatAt().isAfter(now.minus(leaseExpiry));
                boolean heartbeatsAlive = callbacksRecent || leaseHeld;
                // A stage that never reports a counter still sets lastProgressAt once, on entry (see
                // recordStageProgress), and then never again — so without this guard a healthy counterless
                // stage would eventually read as frozen no matter how long it legitimately runs. Such a stage
                // falls to the no-callback arm only without a worker lease; under one it is bounded by Iris's own
                // timeout for that step and the absolute timeout.
                boolean progressFrozen = state.getStageProgress() != null && state.getLastProgressAt().isBefore(now.minus(stallWindow));
                if (heartbeatsAlive && progressFrozen) {
                    failStalledState(state);
                }
                else if (state.getStageStartedAt() != null && state.getStageStartedAt().isBefore(now.minus(slowStageWarningAfter))) {
                    log.warn("slow-stage unit={} stage={} progress={}/{} in_stage_since={} — progress is moving, not intervening", state.getLectureUnit().getId(),
                            state.getCurrentStage(), state.getStageProgress(), state.getStageTotal(), state.getStageStartedAt());
                }
            }
            catch (RuntimeException e) {
                log.error("Stage-liveness check failed for processing state {}, skipping it this pass: {}", state.getId(), e.getMessage());
            }
        }
    }

    /**
     * Fail a stalled run, but only after re-reading the row and re-confirming it is still in flight and
     * still stalled. The batch read that found this candidate may be stale: a terminal success callback
     * can land between the read and this write, and because the entity has no optimistic-lock version, an
     * unconditional {@code save} would revert a just-completed unit to FAILED and wipe its confirmed
     * fingerprint. Re-fetching mirrors {@link #recoverStuckState} and narrows that race; committing
     * through {@link ProcessingStateCallbackService#handleProcessingFailureIfStillLive} instead of a
     * plain save closes the remaining gap between this re-fetch and the failure write itself.
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
        // Pin the lastProgressAt this decision was based on: a stage heartbeat can advance it (without
        // touching phase or token) between this re-fetch and the write, and without pinning it the write
        // would still fail a run that just reported fresh progress.
        callbackService.handleProcessingFailureIfStillLive(freshState, null, freshState.getLastProgressAt(), null);
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
        ZonedDateTime absoluteCutoff = now.minusHours(absoluteTimeoutHours);

        List<LectureUnitProcessingState> stuckStates = processingStateRepository.findStuckStates(List.of(phase), cutoff, absoluteCutoff);

        if (!stuckStates.isEmpty()) {
            log.info("Found {} stuck processing states in phase {} older than {} minutes", stuckStates.size(), phase, timeoutMinutes);

            for (LectureUnitProcessingState state : stuckStates) {
                // Isolated per run, like the stage-liveness check: one failing recovery must not stop the others or the rest of the pass.
                try {
                    recoverStuckState(state, phase, cutoff, absoluteCutoff);
                }
                catch (RuntimeException e) {
                    log.error("Stuck recovery failed for processing state {}, skipping it this pass: {}", state.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Recover a single stuck processing state by resetting to IDLE for re-dispatch.
     * Re-fetches state from DB to avoid overwriting concurrent user changes.
     *
     * @param state          the stuck processing state to recover (used only for ID lookup)
     * @param phase          the expected processing phase
     * @param cutoff         the no-callback cutoff the batch read used to find this candidate
     * @param absoluteCutoff the absolute-timeout cutoff the batch read used to find this candidate
     */
    private void recoverStuckState(LectureUnitProcessingState state, ProcessingPhase phase, ZonedDateTime cutoff, ZonedDateTime absoluteCutoff) {
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

        if (freshState.getIngestionJobToken() == null) {
            // An interrupted content change: no token-matching failure can apply, and resumeInterruptedContentChanges owns it
            log.debug("State {} has no job token, leaving it to the interrupted content change recovery", freshState.getId());
            return;
        }

        // Re-check the predicate findStuckStates selected this row by: a heartbeat may have refreshed lastUpdated since the
        // batch read without touching phase or token, and pinning that fresh value below would still fail a live run.
        boolean silentSinceCutoff = (freshState.getLastHeartbeatAt() == null || freshState.getLastProgressAt() == null) && freshState.getLastUpdated() != null
                && freshState.getLastUpdated().isBefore(cutoff);
        boolean pastAbsoluteDeadline = freshState.getStartedAt() != null && freshState.getStartedAt().isBefore(absoluteCutoff);
        if (!silentSinceCutoff && !pastAbsoluteDeadline) {
            log.debug("State {} is no longer stuck since the batch read, skipping recovery", freshState.getId());
            return;
        }

        log.info("Recovering stuck processing state for unit {}, phase: {}", freshState.getLectureUnit().getId(), phase);

        // Treat stuck jobs as failures: the content itself may cause Iris to hang or crash
        // silently (e.g. malformed PDF, OOM during transcription). Incrementing retryCount
        // ensures poison-pill jobs eventually fail permanently instead of looping forever.
        // Committed through handleProcessingFailureIfStillLive, not a plain save: the phase/token
        // re-checks above narrow the race against a concurrent terminal callback but do not close the
        // remaining gap between this re-fetch and the write itself. Pinning lastUpdated closes it: a
        // checkpoint or heartbeat can bump lastUpdated (without touching phase or retryEligibleAt)
        // between this re-fetch and the write, and without pinning it the write would still fail a run
        // that just proved it was alive.
        callbackService.handleProcessingFailureIfStillLive(freshState, null, null, freshState.getLastUpdated());
    }

    /**
     * Periodically process legacy AttachmentVideoUnits that don't have a processing state yet.
     * This handles units that existed before the automated processing pipeline was deployed.
     * <p>
     * Only processes units from active, non-test courses to avoid unnecessary work. Shares the bound on open
     * background work with the reconcile walk ({@link LectureIngestionReconcileService#spendBacklog}), so the two
     * together never queue more than Iris works off.
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

        // Under the walk's guard: the walk and the backfill share the bound, so neither may queue on a room the other is spending
        reconcileService.spendBacklog(availableSlots -> {
            List<AttachmentVideoUnit> unprocessedUnits = attachmentVideoUnitRepository.findUnprocessedUnitsFromActiveCourses(ZonedDateTime.now(),
                    PageRequest.of(0, availableSlots));
            if (unprocessedUnits.isEmpty()) {
                log.debug("No unprocessed units found for backfill");
                return 0;
            }
            log.info("Found {} unprocessed lecture units to backfill ({} slots available)", unprocessedUnits.size(), availableSlots);
            int triggered = 0;
            for (AttachmentVideoUnit unit : unprocessedUnits) {
                try {
                    log.info("Triggering processing for legacy unit {} (lecture: {}, course: {})", unit.getId(), unit.getLecture() != null ? unit.getLecture().getId() : "unknown",
                            unit.getLecture() != null && unit.getLecture().getCourse() != null ? unit.getLecture().getCourse().getId() : "unknown");
                    processingService.triggerProcessingAsBacklog(unit);
                    triggered++;
                }
                catch (Exception e) {
                    log.error("Failed to trigger processing for unit {}: {}", unit.getId(), e.getMessage());
                }
            }
            return triggered;
        });
    }

    /**
     * Periodically reconcile the vector index against the database: requeue units whose confirmed state,
     * current content, and index stamp diverge, trigger units that never entered the pipeline, and delete
     * orphaned index rows. Each tick starts a pass over all courses, or continues the active one;
     * {@link #continueIngestionReconcile} takes the further steps. See {@link LectureIngestionReconcileService}.
     * Like every scheduled job, it is disabled by setting its schedule property to {@code -}, which also stops the
     * continuation, since only this schedule starts a pass.
     */
    @Scheduled(cron = "${artemis.scheduling.lecture-ingestion-reconcile-time:0 */15 * * * *}")
    public void reconcileIngestionState() {
        if (!featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)) {
            log.debug("LectureContentProcessing feature is disabled, skipping ingestion reconcile");
            return;
        }
        if (!processingService.hasProcessingCapabilities()) {
            log.debug("No processing services available, skipping ingestion reconcile");
            return;
        }

        int spent = reconcileService.startOrContinuePass();
        if (spent > 0) {
            log.info("Ingestion reconcile requeued or triggered {} units", spent);
        }
    }

    /**
     * Take the next step of the active reconcile pass, as soon as the background work it added is done. Without this, a step whose units
     * Iris finishes in seconds (an unchanged unit costs no work) would leave the walk idle until the next cron tick. Does nothing while no
     * pass is active.
     */
    @Scheduled(fixedDelayString = "${artemis.iris.ingestion.reconcile.continuation-interval:PT30S}", initialDelayString = "${artemis.iris.ingestion.reconcile.continuation-interval:PT30S}")
    public void continueIngestionReconcile() {
        if (!featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing) || !processingService.hasProcessingCapabilities()) {
            return;
        }
        int spent = reconcileService.continuePass();
        if (spent > 0) {
            log.info("Ingestion reconcile requeued or triggered {} units", spent);
        }
    }
}
