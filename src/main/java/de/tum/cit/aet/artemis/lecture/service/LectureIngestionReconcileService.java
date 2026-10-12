package de.tum.cit.aet.artemis.lecture.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateReconcileRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Converges the Pyris vector index toward what the database says should be ingested.
 * <p>
 * The event-driven pipeline (dispatch, callbacks, retries, restart recovery) covers the happy and
 * near-happy paths. This service closes the remaining gap by comparing the processing state ledger
 * against two sources of truth and requeueing whatever diverges:
 * <ul>
 * <li><b>The current content</b>: a DONE unit whose {@code confirmedFingerprint} is missing (legacy row)
 * or no longer matches the fingerprint of its current sources needs a re-run.</li>
 * <li><b>The index itself</b>: the Pyris ingestion census reports what the index actually holds, including
 * the fingerprint stamp of each unit row. A DONE unit whose stamp is missing or different lost its data
 * (backup restore, collection recreate, raced delete) and needs a re-run; a censused unit that no longer
 * exists in the database is an orphan and gets deleted, as do the rows of a unit whose lecture is a
 * tutorial lecture.</li>
 * </ul>
 * The reconciler never certifies anything itself: every divergence is resolved by requeueing the unit
 * through the normal pipeline, whose end-of-run audit is the only thing that produces a confirmed
 * fingerprint. Re-runs of genuinely unchanged, complete content are cheap because the Iris pipeline
 * skips the expensive per-page work in that case.
 * <p>
 * The walk is work-driven and cursor-based. A pass walks all courses (including inactive and archived ones
 * the backfill never reaches) in steps of a few courses. Each step may only add as many units as keep the
 * open background work (queued, running, or waiting for a retry) below a configured bound, so a large
 * backlog drains as fast as Iris finishes it, underneath fresh uploads, instead of flooding the queue or
 * idling while the units it requeued are already done. The cron schedule starts a pass; a short continuation
 * job takes the next step while a pass is active. The cursor lives in memory: the walker only runs on the
 * scheduling node (one node per installation), and losing the cursor on a restart merely restarts the walk
 * from the beginning, which is idempotent.
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class LectureIngestionReconcileService {

    private static final Logger log = LoggerFactory.getLogger(LectureIngestionReconcileService.class);

    private final LectureUnitProcessingStateRepository processingStateRepository;

    /** The guarded writes this walk commits; see the repository's own javadoc for why they live apart. */
    private final LectureUnitProcessingStateReconcileRepository reconcileStateRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final LectureUnitContentFingerprintService contentFingerprintService;

    private final LectureContentProcessingService processingService;

    private final LectureTranscriptionRepository transcriptionRepository;

    /**
     * Dispatch priority of reconcile requeues: behind fresh work (0) and behind nothing else,
     * so draining backlog and healing drift never starves a fresh upload.
     */
    static final int RECONCILE_DISPATCH_PRIORITY = 2;

    private final int coursesPerRun;

    /**
     * Upper bound on open background units (backfill and reconcile work that is queued, running, or waiting for a retry). A walk step adds
     * at most the difference to this bound, so the walk adds work only as fast as Iris finishes it.
     */
    private final int maxBacklog;

    private final double qualityThreshold;

    /**
     * How long a unit must sit FAILED before the reconciler revives it for another attempt. This is
     * the long-loop counterpart to the scheduler's short retry loop: the short loop (five attempts,
     * minutes-scale backoff) catches momentary faults, while this catches failures whose cause is
     * slow to clear — a locked resource, an API key that was later restored, a vector store that was
     * down for hours. The cooldown is measured from {@code lastUpdated}, which for a FAILED row is
     * the moment it failed, so no extra state is needed to space revivals per unit.
     */
    private final Duration failedRevivalCooldown;

    /**
     * Error keys that are permanent by nature: the content or configuration is the problem, and no
     * amount of waiting fixes it (a private/too-long video, an unreadable attachment, a wrong unit
     * type). These are never revived automatically — they stay FAILED for the manual retry button.
     * Every other failure (the generic {@code processingFailed} that vector-store, audit, timeout,
     * and unknown failures collapse to) is treated as transient and eligible for revival.
     */
    private static final Set<String> PERMANENT_ERROR_KEYS = Set.of("artemisApp.attachmentVideoUnit.processing.error.youtubePrivate",
            "artemisApp.attachmentVideoUnit.processing.error.youtubeLive", "artemisApp.attachmentVideoUnit.processing.error.youtubeTooLong",
            "artemisApp.attachmentVideoUnit.processing.error.youtubeUnavailable", "artemisApp.attachmentVideoUnit.processing.error.invalidUnitType",
            "artemisApp.attachmentVideoUnit.processing.error.attachmentUnreadable", "artemisApp.attachmentVideoUnit.processing.error.noIngestibleContent");

    /**
     * How many times a FAILED unit is revived without an intervening successful completion before it is left
     * FAILED for manual attention. The backstop for a generic (unclassified) error that is really permanent:
     * each revival waits out the cooldown and the next walk visit of its course, so this spans at least ten hours of retrying, longer than
     * any transient outage, and the
     * count resets on a successful DONE, so it never shortens genuine transient recovery.
     */
    private final int maxRevivals;

    private final AtomicLong courseCursor = new AtomicLong(0);

    /**
     * Whether a pass is running: started by the cron schedule, continued step by step, and ended when the walk wraps around after the last
     * course. Separate from {@link #courseResume}, which only marks a pause inside one course.
     */
    private final AtomicBoolean passActive = new AtomicBoolean(false);

    /** How many units the active pass requeued or triggered so far, for the log line at its end. */
    private final AtomicInteger passSpent = new AtomicInteger(0);

    /**
     * Held from reading the free backlog room until the units it allows are queued, by a walk step and by the backfill alike. Without it,
     * a cron run, a continuation step and the backfill (separate scheduler threads) could each read the same room and together queue more
     * than the bound. In memory is enough: only the scheduling node walks and backfills.
     */
    private final AtomicBoolean spendingBacklog = new AtomicBoolean(false);

    /**
     * Where a course the budget ran out in resumes, or {@code null} when the next pass starts at a course boundary. Resuming after the last visited unit, rather than
     * revisiting the course from its start, is what keeps the walk moving: a course with more units that diverge again after every re-ingest than one pass can spend on
     * would otherwise hold the walk on its first units for good and starve every course after it.
     */
    private final AtomicReference<CourseResume> courseResume = new AtomicReference<>();

    private record CourseResume(long courseId, long afterUnitId) {
    }

    /**
     * The outcome of one visit to a course.
     *
     * @param spent             how many requeues/triggers the visit spent
     * @param pausedAfterUnitId the last unit visited when the budget ran out with units still ahead, or {@code null} when the visit reached the end of the course
     */
    record CourseVisit(int spent, @Nullable Long pausedAfterUnitId) {
    }

    public LectureIngestionReconcileService(LectureUnitProcessingStateRepository processingStateRepository, LectureUnitProcessingStateReconcileRepository reconcileStateRepository,
            AttachmentVideoUnitRepository attachmentVideoUnitRepository, Optional<IrisLectureApi> irisLectureApi, LectureUnitContentFingerprintService contentFingerprintService,
            LectureContentProcessingService processingService, @Value("${artemis.iris.ingestion.reconcile.courses-per-run:10}") int coursesPerRun,
            @Value("${artemis.iris.ingestion.reconcile.max-backlog:3}") int maxBacklog, @Value("${artemis.iris.ingestion.reconcile.quality-threshold:0.8}") double qualityThreshold,
            @Value("${artemis.iris.ingestion.reconcile.failed-revival-cooldown:PT1H}") Duration failedRevivalCooldown,
            @Value("${artemis.iris.ingestion.reconcile.max-revivals:10}") int maxRevivals, LectureTranscriptionRepository transcriptionRepository) {
        this.processingStateRepository = processingStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.irisLectureApi = irisLectureApi;
        this.contentFingerprintService = contentFingerprintService;
        this.processingService = processingService;
        this.coursesPerRun = coursesPerRun;
        this.maxBacklog = maxBacklog;
        this.qualityThreshold = qualityThreshold;
        this.failedRevivalCooldown = failedRevivalCooldown;
        this.maxRevivals = maxRevivals;
        this.transcriptionRepository = transcriptionRepository;
    }

    /**
     * Start a pass unless one is active, then take its next step. Called by the cron schedule; a tick during an active pass continues it
     * where it stands instead of starting over.
     *
     * @return how many units this step requeued or triggered
     */
    public int startOrContinuePass() {
        if (passActive.compareAndSet(false, true)) {
            passSpent.set(0);
            log.info("Ingestion reconcile pass started");
        }
        return walkNextCourses();
    }

    /**
     * Take the next step of the active pass, if there is one. Called by the continuation job, so a pass moves on as soon as the
     * background work it added is done instead of waiting for the next cron tick.
     *
     * @return how many units this step requeued or triggered
     */
    public int continuePass() {
        if (!passActive.get()) {
            return 0;
        }
        return walkNextCourses();
    }

    /**
     * How many more background units may be queued now: the configured bound minus the background units still open. Shared by the walk and
     * the backfill, so together they never keep more than the bound in flight.
     *
     * @return the free room, at most the configured bound; zero or less when the backlog is full
     */
    int backlogBudget() {
        return maxBacklog - (int) processingStateRepository.countOpenBackgroundUnits(LectureContentProcessingService.BACKLOG_DISPATCH_PRIORITY);
    }

    /**
     * Queue background work within the free backlog room: reads the room and runs {@code spend} with it while no other walk step or
     * backfill does the same, so together they never queue more than the bound. Does nothing when the room is zero or another caller is
     * spending it right now; the next scheduled run tries again.
     *
     * @param spend queues at most the given number of units and returns how many it queued
     * @return how many units {@code spend} queued, 0 when it did not run
     */
    public int spendBacklog(IntUnaryOperator spend) {
        if (!spendingBacklog.compareAndSet(false, true)) {
            log.debug("Background ingestion work skipped: another walk step or backfill is queueing right now");
            return 0;
        }
        try {
            int budget = backlogBudget();
            if (budget <= 0) {
                log.debug("Background ingestion work paused: the backlog of {} open units is full", maxBacklog);
                return 0;
            }
            return spend.applyAsInt(budget);
        }
        finally {
            spendingBacklog.set(false);
        }
    }

    /**
     * Take one step of the walk: reconcile the next slice of courses, spending at most the free backlog room. With no room, it returns
     * before touching the cursor or the resume point, so the step resumes exactly there once the room frees up. When no course is left
     * after the cursor, the pass ends and the next one starts from the beginning.
     *
     * @return how many units were requeued or newly triggered
     */
    int walkNextCourses() {
        if (irisLectureApi.isEmpty()) {
            return 0;
        }
        return spendBacklog(budget -> {
            List<Long> courseIds = attachmentVideoUnitRepository.findReconcileCourseIdsAfter(courseCursor.get(), PageRequest.of(0, coursesPerRun));
            if (courseIds.isEmpty()) {
                courseCursor.set(0);
                courseResume.set(null);
                if (passActive.getAndSet(false)) {
                    log.info("Ingestion reconcile pass completed, {} units requeued or triggered", passSpent.get());
                }
                return 0;
            }
            int spent = walkCourses(courseIds, budget);
            passSpent.addAndGet(spent);
            return spent;
        });
    }

    private int walkCourses(List<Long> courseIds, int budget) {
        CourseResume resume = courseResume.getAndSet(null);
        int spent = 0;
        for (Long courseId : courseIds) {
            CourseVisit visit = new CourseVisit(0, null);
            long afterUnitId = resume != null && resume.courseId() == courseId ? resume.afterUnitId() : 0;
            // Isolate each course so one failing course (e.g. its Iris census call throwing) does not
            // abort the pass before the cursor advances, which would re-hit the same course every run
            // and permanently block reconciliation of every course after it.
            try {
                visit = reconcileCourse(courseId, budget - spent, afterUnitId);
            }
            catch (RuntimeException e) {
                log.error("Reconcile: course {} failed this pass and was skipped: {}", courseId, e.getMessage());
            }
            spent += visit.spent();
            if (visit.spent() > 0) {
                log.info("reconcile-course course={} requeued_or_triggered={}", courseId, visit.spent());
            }
            if (visit.pausedAfterUnitId() != null) {
                // The budget ran out inside this course: the next step comes back to it (the cursor sits just
                // before it) and continues after the last unit visited here instead of skipping the rest.
                courseCursor.set(courseId - 1);
                courseResume.set(new CourseResume(courseId, visit.pausedAfterUnitId()));
                break;
            }
            courseCursor.set(courseId);
            if (spent >= budget) {
                // The budget ran out exactly at the end of this course: the next step starts with the next course.
                break;
            }
        }
        return spent;
    }

    /**
     * Reconcile a single course: requeue divergent units, trigger units that never entered the pipeline,
     * and delete orphaned index rows.
     *
     * @param courseId      the course to reconcile
     * @param requeueBudget how many requeues/triggers this call may spend
     * @return how many requeues/triggers were spent
     */
    int reconcileCourse(long courseId, int requeueBudget) {
        return reconcileCourse(courseId, requeueBudget, 0).spent();
    }

    /**
     * Reconcile a single course starting after the given unit, as {@link #reconcileCourse(long, int)} does from its start.
     *
     * @param courseId      the course to reconcile
     * @param requeueBudget how many requeues/triggers this call may spend
     * @param afterUnitId   only units with a larger id are visited; 0 visits the whole course
     * @return what the visit spent, and where it paused if the budget ran out with units still ahead
     */
    CourseVisit reconcileCourse(long courseId, int requeueBudget, long afterUnitId) {
        if (requeueBudget <= 0) {
            // Nothing may be spent: pause before the first unit still to visit, so the course is not skipped
            return new CourseVisit(0, afterUnitId);
        }
        IngestionCensusDTO fetchedCensus = irisLectureApi.get().getIngestionCensus(courseId);
        if (fetchedCensus != null && fetchedCensus.truncated()) {
            // The unit-row scan hit its cap, so any unit-row-derived fact (row counts, stamps, lecture ids) may be missing for
            // any unit: no census-based decision is safe for this course.
            log.warn("Reconcile: the census of course {} is truncated; skipping every census-based decision for it this pass", courseId);
        }
        IngestionCensusDTO census = fetchedCensus != null && !fetchedCensus.truncated() ? fetchedCensus : null;
        boolean censusAvailable = census != null;
        Map<Long, IngestionCensusUnitDTO> censusByUnitId = censusAvailable
                ? census.units().stream().collect(Collectors.toMap(IngestionCensusUnitDTO::lectureUnitId, Function.identity(), (first, second) -> first))
                : Map.of();
        Set<Long> unitsWithUnindexedTranscript = findUnitsWithUnindexedTranscript(censusByUnitId);

        List<AttachmentVideoUnit> units = attachmentVideoUnitRepository.findAllWithAttachmentByCourseId(courseId);
        Map<Long, LectureUnitProcessingState> stateByUnitId = processingStateRepository.findWithLectureUnitByCourseId(courseId).stream()
                .collect(Collectors.toMap(state -> state.getLectureUnit().getId(), Function.identity(), (first, second) -> first));

        int spent = 0;
        Long lastVisitedUnitId = null;
        Long pausedAfterUnitId = null;
        // Units come ordered by id, which is what makes resuming after an id skip exactly the units already visited.
        for (AttachmentVideoUnit unit : units) {
            if (unit.getId() <= afterUnitId) {
                continue;
            }
            if (spent >= requeueBudget) {
                pausedAfterUnitId = lastVisitedUnitId;
                break;
            }
            lastVisitedUnitId = unit.getId();
            // Isolate each unit: an unexpected failure on one (a malformed link, a bad census entry)
            // must not abort the course, which would otherwise leave the cursor stuck and starve every
            // later course of reconciliation.
            try {
                LectureUnitProcessingState state = stateByUnitId.get(unit.getId());
                if (!processingService.hasProcessableContent(unit)) {
                    // Content removed, or only a link to a video Iris cannot transcribe: an earlier unit with content still records its markers
                    // until the removed-content cleanup succeeded, which settles it as nothing indexed. Retry that cleanup here.
                    if (state != null && hasRetryableContentCleanup(state) && processingService.retryRemovedContentCleanup(unit, state.getId())) {
                        log.info("Reconcile: retried the cleanup of removed content for unit {} of course {}", unit.getId(), courseId);
                        spent++;
                    }
                    continue;
                }

                if (state == null) {
                    // Never entered the pipeline: typically a pre-pipeline unit in an inactive or archived
                    // course, which the active-course backfill deliberately does not reach.
                    log.info("Reconcile: triggering processing for unit {} of course {} (no processing state)", unit.getId(), courseId);
                    processingService.triggerProcessingAsBacklog(unit);
                    spent++;
                    continue;
                }
                spent += switch (state.getPhase()) {
                    case DONE -> reconcileDoneUnit(unit, state, census, censusByUnitId.get(unit.getId()), unitsWithUnindexedTranscript.contains(unit.getId()));
                    case SKIPPED -> reconcileSkippedUnit(unit, state);
                    // A FAILED unit is revived once its transient cause has had time to clear (see
                    // reconcileFailedUnit); only permanent content failures stay terminal for the manual button.
                    // A truncated entry is no evidence about this unit, so it counts as no census for the revival decision
                    case FAILED -> reconcileFailedUnit(state, censusAvailable && !isTruncated(censusByUnitId.get(unit.getId())));
                    // IDLE, TRANSCRIBING, and INGESTING are owned by the normal dispatch and stuck-recovery machinery.
                    default -> 0;
                };
            }
            catch (RuntimeException e) {
                log.error("Reconcile: skipping unit {} of course {} after an unexpected error: {}", unit.getId(), courseId, e.getMessage());
            }
        }

        if (censusAvailable) {
            deleteOrphanedIndexRows(census, units);
        }
        return new CourseVisit(spent, pausedAfterUnitId);
    }

    /**
     * Reconcile a DONE unit against its current content and the index reality.
     */
    private int reconcileDoneUnit(AttachmentVideoUnit unit, LectureUnitProcessingState state, @Nullable IngestionCensusDTO census, @Nullable IngestionCensusUnitDTO censusEntry,
            boolean transcriptUnindexed) {
        String currentFingerprint;
        try {
            currentFingerprint = contentFingerprintService.computeFingerprint(unit);
        }
        catch (RuntimeException e) {
            // An unreadable attachment surfaces as IllegalStateException; a malformed link surfaces
            // as IllegalArgumentException from URI.create. Either way it is a local problem we cannot
            // fix here, so skip this unit rather than letting the exception abort the walk.
            log.warn("Reconcile: cannot compute fingerprint for unit {}, skipping: {}", unit.getId(), e.getMessage());
            return 0;
        }

        // Snapshot the confirmed fingerprint before any decision so requeueForReconcile can detect a
        // concurrent completion that re-confirmed the unit between the batch read and the write.
        String observedFingerprint = state.getConfirmedFingerprint();
        if (observedFingerprint == null || !observedFingerprint.equals(currentFingerprint)) {
            if (processingService.hasVideoSourceChanged(unit, state)) {
                // The video changed without the update path firing, so the stored transcript belongs to the previous
                // video. A plain requeue would dispatch straight to INGESTING with it; the content-change path deletes it
                // and cleans up Iris before requeueing, exactly as an edit of the unit would.
                log.info("Reconcile: video source of unit {} changed without the update path, handing it to the content-change path", unit.getId());
                processingService.triggerProcessingAsBacklog(unit);
                return 1;
            }
            if (observedFingerprint == null) {
                // Legacy row that predates verification: the pipeline's skip-check decides whether a re-run has anything to do.
                return requeueForReconcile(state, null, "no confirmed fingerprint for the current content", ReconcileIntent.plain()) ? 1 : 0;
            }
            // The content changed without the update path firing. The skip-check compares versions, not content, so only a
            // forced rebuild is certain to replace what the index holds.
            return requeueForReconcile(state, observedFingerprint, "the content changed since its fingerprint was confirmed", ReconcileIntent.forcingRebuild()) ? 1 : 0;
        }
        // A truncated entry undercounts this unit's rows, so none of the census checks below can be trusted for it.
        if (census != null && (censusEntry == null || !censusEntry.truncated())) {
            if (censusEntry == null || censusEntry.unitRowCount() == 0 || !currentFingerprint.equals(censusEntry.contentFingerprint())) {
                // The run was confirmed, but the index no longer holds a matching stamp: the data was lost
                // or replaced after the fact (backup restore, collection recreate, raced delete). Force a
                // full re-ingest so the run rewrites the unit instead of the skip-check treating it as done.
                return requeueForReconcile(state, observedFingerprint, "index stamp missing or different from the confirmed fingerprint", ReconcileIntent.forcingRebuild()) ? 1 : 0;
            }
            // Re-queue on any structural divergence between what the index holds and what a complete unit
            // must hold. Every signal below is healed by a forced full re-ingest and then reported clean, so
            // the re-queue converges and stops rather than looping: generations counted after excluding inert
            // object-store ghosts (so a ghost-carrying unit is not re-queued forever), rows lost below the
            // certified count, a page-coverage hole against the PDF, a PDF unit left with no chunks, duplicate
            // unit rows, missing slide segments, and legacy null display numbers. A benign single-generation
            // count difference is deliberately not a signal, so a stale expectation alone does not cause churn.
            // The same stored-PDF rule the payload uses: an external .pdf link is ingested as video-only and must not be expected to have page chunks.
            boolean hasPdf = unit.getAttachment() != null && unit.getAttachment().isStoredPdf();
            String divergence = divergenceReason(censusEntry, hasPdf, transcriptUnindexed);
            if (divergence != null) {
                return requeueForReconcile(state, observedFingerprint, divergence, ReconcileIntent.forcingRebuild()) ? 1 : 0;
            }
            if (isQualityRequeueDue(state, census, censusEntry)) {
                // A newer pipeline version can improve this low-quality unit. The versioned edge
                // (at most one quality requeue per pipeline version) is what makes this terminate;
                // the forced re-run keeps the stored generation when it does not score better.
                return requeueForReconcile(state, observedFingerprint,
                        "quality " + censusEntry.qualityScore() + " below threshold and pipeline version " + census.currentPipelineVersion() + " is available",
                        ReconcileIntent.forQuality(census.currentPipelineVersion())) ? 1 : 0;
            }
        }
        return 0;
    }

    /**
     * The first structural divergence that warrants a forced full re-ingest of a DONE unit, or {@code null}
     * when the unit is structurally complete. Every reason here is resolved deterministically by a re-ingest
     * (which then reports clean), so acting on it converges instead of looping — unlike a signal keyed on the
     * raw, ghost-inflated counts. All counts are the census's object-store-confirmed counts (inert ghost rows
     * excluded), so a ghost-carrying but otherwise healthy unit yields no reason.
     *
     * @param censusEntry the unit's confirmed index state
     * @param hasPdf      whether the unit has a PDF attachment (so page chunks and slide segments are expected)
     * @return a human-readable reason, or {@code null} when nothing diverges
     */
    @Nullable
    private static String divergenceReason(IngestionCensusUnitDTO censusEntry, boolean hasPdf, boolean transcriptUnindexed) {
        // Stale generations coexist. Counted after excluding object-store ghosts, so a healed unit with inert
        // ghost rows reports 1 and is left alone; a genuine second real generation is rewritten to one.
        if (censusEntry.generationCount() > 1) {
            return censusEntry.generationCount() + " ingestion generations coexist";
        }
        // Duplicate unit rows (a crash between the unit-row write and its purge). Also ghost-excluded.
        if (censusEntry.unitRowCount() > 1) {
            return censusEntry.unitRowCount() + " unit rows coexist";
        }
        // A PDF unit that ended DONE with no page chunks: the content was lost or never written. Checked
        // independently of the certified expectation so a legacy/pre-ledger empty unit is still healed.
        if (hasPdf && censusEntry.chunkCount() == 0) {
            return "a PDF unit has no page chunks";
        }
        // Rows lost below the count the last certified run recorded.
        if (censusEntry.expectedChunkCount() != null && censusEntry.chunkCount() < censusEntry.expectedChunkCount()) {
            return "chunk count fell below the certified expectation";
        }
        // A hole in the page coverage: some page of the source PDF produced no chunk (the pipeline's own
        // skip-check requires every page 1..N to be present, so a gap means an incomplete run).
        if (censusEntry.missingPageCount() > 0) {
            return censusEntry.missingPageCount() + " page(s) missing from the chunk coverage";
        }
        // Slides present but their per-slide segment summaries are missing.
        if (hasPdf && censusEntry.chunkCount() > 0 && censusEntry.segmentCount() == 0) {
            return "slide segments are missing for a unit with page chunks";
        }
        // A completed, non-empty transcript is stored in Artemis but the index holds none of its rows.
        if (transcriptUnindexed) {
            return "a completed transcript is not indexed";
        }
        // Legacy chunks whose display page number was never resolved; a re-ingest repopulates real numbers.
        if (censusEntry.nullDisplayCount() > 0) {
            return censusEntry.nullDisplayCount() + " chunk(s) have an unresolved display page number";
        }
        return null;
    }

    private boolean isQualityRequeueDue(LectureUnitProcessingState state, IngestionCensusDTO census, IngestionCensusUnitDTO censusEntry) {
        Integer currentVersion = census.currentPipelineVersion();
        return currentVersion != null && censusEntry.qualityScore() != null && censusEntry.qualityScore() < qualityThreshold && censusEntry.pipelineVersion() != null
                && censusEntry.pipelineVersion() < currentVersion && !Objects.equals(state.getLastQualityPipelineVersion(), currentVersion);
    }

    /**
     * Requeue a SKIPPED unit once it became processable, e.g. after Iris was enabled for the course.
     * Without this, SKIPPED units would wait for a content change forever.
     */
    private int reconcileSkippedUnit(AttachmentVideoUnit unit, LectureUnitProcessingState state) {
        if (!irisLectureApi.get().isLectureUnitProcessable(unit)) {
            return 0;
        }
        return requeueForReconcile(state, state.getConfirmedFingerprint(), "unit became processable after being skipped", ReconcileIntent.plain()) ? 1 : 0;
    }

    /**
     * Requeue a divergent unit, guarding against a stale batch read.
     * <p>
     * The reconcile walk hashes PDFs and calls Iris between reading the state batch and this write,
     * and the entity carries no {@code @Version}, so a blind {@code save()} would merge a stale
     * snapshot over a row that changed underneath it. This re-fetches and only requeues while the row
     * is still in the phase that justified the decision <em>and</em> still carries the confirmed
     * fingerprint observed at batch-read time — so a concurrent completion that re-confirmed the unit
     * (same phase, new fingerprint) is left alone rather than reverted. The branch-specific intent
     * (clear fingerprint, force a re-run, stamp the quality version) is applied to the fresh row only
     * after the guard passes. Mirrors the scheduler's stalled and stuck re-confirmation.
     *
     * @param state               the batch-read state that was found divergent
     * @param observedFingerprint the confirmed fingerprint seen at batch-read time (before any mutation)
     * @param reason              human-readable reason for the log line
     * @param applyIntent         mutates the fresh row with the branch-specific decision before requeue
     */
    /**
     * Revive a FAILED unit for another attempt once its failure has had time to clear — the long-loop
     * retry that complements the scheduler's short one. A unit is revived only when all of the following
     * hold, so genuinely-broken content and a sick store are never re-run pointlessly:
     * <ul>
     * <li>the failure was <em>transient-class</em> (not a permanent content/config error, see
     * {@link #PERMANENT_ERROR_KEYS}) — a private video is never revived, a store outage always is;</li>
     * <li>it has sat FAILED longer than {@link #failedRevivalCooldown}, measured from {@code lastUpdated},
     * which both proves the cause had time to clear and spaces successive revivals per unit;</li>
     * <li>the vector store answered this run's census, so a revival is not dispatched into a store that is
     * itself still down.</li>
     * </ul>
     * Revivals are bounded, but generously: after {@link #maxRevivals} revivals without an intervening
     * successful completion the unit is left FAILED for the manual retry button instead of being re-attempted
     * forever. This is the backstop for a genuinely-unprocessable unit that reports a generic (unclassified)
     * error and so is not in {@link #PERMANENT_ERROR_KEYS} — without it, such a unit would revive every
     * cooldown indefinitely. The cap does not shorten recovery from a real outage: a store that is down does
     * not answer the census, so those passes neither revive nor count (only revivals dispatched into a
     * healthy store that still fail are counted), and the count resets on a successful DONE, so a transient
     * failure that later succeeds regains a full budget. The revival is committed by {@link #reviveFailedUnit},
     * whose guard pins the exact {@code errorKey}, {@code lastUpdated} and {@code revivalCount} read here — not
     * just the phase and claim {@link #requeueForReconcile} pins — so a unit that fails again for a different
     * reason before the write lands is never wrongly revived.
     *
     * @param state           the FAILED processing state from the batch read
     * @param censusAvailable whether the vector store answered the census for this course
     * @return 1 if the unit was revived, 0 otherwise
     */
    private int reconcileFailedUnit(LectureUnitProcessingState state, boolean censusAvailable) {
        if (!censusAvailable) {
            return 0;
        }
        if (state.getErrorKey() != null && PERMANENT_ERROR_KEYS.contains(state.getErrorKey())) {
            return 0;
        }
        if (state.getRevivalCount() >= maxRevivals) {
            // Exhausted its revival budget without ever succeeding: treat a generic error that keeps
            // recurring as effectively permanent and leave it FAILED for manual attention rather than
            // re-attempting it forever.
            return 0;
        }
        if (state.getLastUpdated() == null || state.getLastUpdated().isAfter(ZonedDateTime.now().minus(failedRevivalCooldown))) {
            return 0;
        }
        return reviveFailedUnit(state, "transient failure cooled down, reviving for another attempt") ? 1 : 0;
    }

    /**
     * @return true if the row still matched the decision and was requeued; false if its state changed
     *         since the batch read, in which case nothing was written and the caller's budget must not be
     *         charged for this unit
     */
    private boolean requeueForReconcile(LectureUnitProcessingState state, String observedFingerprint, String reason, ReconcileIntent intent) {
        long unitId = state.getLectureUnit().getId();
        int updated = reconcileStateRepository.requeueForReconcileIfUnchanged(state.getId(), state.getPhase(), observedFingerprint,
                intent.clearsConfirmedFingerprint() ? null : observedFingerprint, intent.forceReingest(), intent.qualityPipelineVersion(), RECONCILE_DISPATCH_PRIORITY,
                ZonedDateTime.now());
        if (updated == 0) {
            log.debug("Reconcile: skipping requeue of unit {} — its state changed since the batch read", unitId);
            return false;
        }
        // Mirror the committed outcome onto the batch-read snapshot, field for field with the statement above, so the
        // caller is never left holding a state that describes a row no longer shaped that way.
        if (intent.clearsConfirmedFingerprint()) {
            state.setConfirmedFingerprint(null);
        }
        if (Boolean.TRUE.equals(intent.forceReingest())) {
            state.setForceReingest(true);
        }
        if (intent.qualityPipelineVersion() != null) {
            state.setLastQualityPipelineVersion(intent.qualityPipelineVersion());
        }
        state.resetRetryCount();
        state.requeue();
        state.setDispatchPriority(RECONCILE_DISPATCH_PRIORITY);
        log.info("Reconcile: requeued unit {} ({})", unitId, reason);
        return true;
    }

    /**
     * Revive a FAILED unit through the dedicated guard that pins the exact facts {@link #reconcileFailedUnit}
     * decided on, rather than {@link #requeueForReconcile}'s phase-and-fingerprint guard: a revival's decision
     * rests on {@code errorKey}, {@code lastUpdated} and {@code revivalCount}, none of which that guard checks, so
     * a unit that is claimed, dispatched, and fails right back to FAILED for a different reason before this write
     * lands would otherwise still match it and have its newer failure silently erased.
     *
     * @return true if the row still matched the FAILED decision and was revived; false if its state changed
     *         since the batch read, in which case nothing was written and the caller's budget must not be
     *         charged for this unit
     */
    private boolean reviveFailedUnit(LectureUnitProcessingState state, String reason) {
        long unitId = state.getLectureUnit().getId();
        int updated = reconcileStateRepository.reviveFailedIfUnchanged(state.getId(), state.getErrorKey(), state.getLastUpdated(), state.getRevivalCount(),
                RECONCILE_DISPATCH_PRIORITY, ZonedDateTime.now());
        if (updated == 0) {
            log.debug("Reconcile: skipping revival of unit {} — its state changed since the batch read", unitId);
            return false;
        }
        // Mirror the committed outcome onto the batch-read snapshot, field for field with the statement above.
        state.setRevivalCount(state.getRevivalCount() + 1);
        state.resetRetryCount();
        state.requeue();
        state.setDispatchPriority(RECONCILE_DISPATCH_PRIORITY);
        log.info("Reconcile: revived unit {} ({})", unitId, reason);
        return true;
    }

    /**
     * The branch-specific part of a reconcile requeue, applied by the same statement that commits the requeue.
     * Everything a requeue always does (phase, claim, run markers, retry budget, dispatch order) is fixed; this
     * carries only what differs between the reasons a unit is requeued. Revival is not one of these branches: it
     * is guarded by different fields entirely and goes through {@link #reviveFailedUnit} instead.
     *
     * @param clearsConfirmedFingerprint whether the confirmed fingerprint is dropped, which is what makes the next
     *                                       run rewrite the unit instead of the skip-check treating it as complete;
     *                                       otherwise it is left exactly as observed
     * @param forceReingest              {@code TRUE} to force a full rewrite, {@code null} to keep the current flag
     * @param qualityPipelineVersion     the pipeline version to stamp for a quality requeue, {@code null} to keep
     */
    private record ReconcileIntent(boolean clearsConfirmedFingerprint, Boolean forceReingest, Integer qualityPipelineVersion) {

        /** Requeue exactly as the unit stands: no fingerprint change, no forced rewrite. */
        private static ReconcileIntent plain() {
            return new ReconcileIntent(false, null, null);
        }

        /**
         * Drop the confirmed fingerprint and force the rewrite. Without forcing, the pipeline's skip-check would
         * treat the unit as complete and skip it, so the requeue would repeat forever without ever rewriting it.
         */
        private static ReconcileIntent forcingRebuild() {
            return new ReconcileIntent(true, true, null);
        }

        /** Force a rewrite on the newer pipeline, stamping the version so the unit is requeued at most once for it. */
        private static ReconcileIntent forQuality(int pipelineVersion) {
            return new ReconcileIntent(false, true, pipelineVersion);
        }
    }

    /**
     * Units whose census entry reports no transcript rows although Artemis stores a completed, non-empty transcript for them. Only the
     * units the census reports without transcript rows are loaded, so a healthy course costs one empty lookup at most.
     */
    private Set<Long> findUnitsWithUnindexedTranscript(Map<Long, IngestionCensusUnitDTO> censusByUnitId) {
        List<Long> candidates = censusByUnitId.values().stream().filter(entry -> entry.unitRowCount() > 0 && entry.transcriptionCount() == 0 && !entry.truncated())
                .map(IngestionCensusUnitDTO::lectureUnitId).toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }
        return transcriptionRepository.findAllByLectureUnit_IdInAndTranscriptionStatus(candidates, TranscriptionStatus.COMPLETED).stream()
                .filter(transcription -> transcription.getSegments() != null && !transcription.getSegments().isEmpty()).map(transcription -> transcription.getLectureUnit().getId())
                .collect(Collectors.toSet());
    }

    /** Whether the census entry of a unit undercounts it because one of its scans hit the row cap. */
    private static boolean isTruncated(@Nullable IngestionCensusUnitDTO censusEntry) {
        return censusEntry != null && censusEntry.truncated();
    }

    /**
     * Whether a unit without processable content still records content markers from a removed-content cleanup that did not finish,
     * in a phase the cleanup claim accepts: at rest, and not waiting for a retry.
     */
    private static boolean hasRetryableContentCleanup(LectureUnitProcessingState state) {
        boolean hasMarkers = (state.getVideoSourceHash() != null && !state.getVideoSourceHash().isBlank()) || state.getAttachmentVersion() != null;
        boolean atRest = switch (state.getPhase()) {
            case DONE, SKIPPED -> true;
            case FAILED -> state.getRetryEligibleAt() == null;
            default -> false;
        };
        return hasMarkers && atRest && state.getIngestionJobToken() == null;
    }

    /**
     * Delete index rows whose lecture unit no longer exists in the database, or whose lecture is a tutorial lecture.
     * The census reports a unit as long as ANY collection still holds rows for it, so this is what
     * garbage-collects leftovers from failed deletions and superseded runs, and the rows of units whose
     * lecture was marked as a tutorial lecture after they were ingested, which nothing else removes.
     */
    private void deleteOrphanedIndexRows(IngestionCensusDTO census, List<AttachmentVideoUnit> courseUnits) {
        // The course unit list excludes tutorial lectures, so existence is checked against the database
        // rather than against the list: an id that exists at all is not an orphan. One batched query
        // resolves all census ids at once instead of an existsById per row.
        List<Long> censusUnitIds = census.units().stream().map(IngestionCensusUnitDTO::lectureUnitId).toList();
        if (censusUnitIds.isEmpty()) {
            return;
        }
        Set<Long> existingUnitIds = attachmentVideoUnitRepository.findExistingIds(censusUnitIds);
        List<IngestionCensusUnitDTO> orphanEntries = census.units().stream().filter(entry -> !existingUnitIds.contains(entry.lectureUnitId()) && !entry.truncated()).toList();
        // The Iris deletion matches on the lecture id; without one it would delete nothing while reporting success.
        orphanEntries.stream().filter(entry -> entry.lectureId() == null).forEach(
                entry -> log.warn("Reconcile: orphaned unit {} of course {} reports no lecture id, cannot delete its index rows", entry.lectureUnitId(), census.courseId()));
        List<IngestionJobIdentityDTO> orphans = orphanEntries.stream().filter(entry -> entry.lectureId() != null)
                .map(entry -> new IngestionJobIdentityDTO(census.courseId(), entry.lectureId(), entry.lectureUnitId())).toList();
        // A tutorial lecture unit still exists, so it is never an orphan, and the per-unit loop excludes it.
        List<IngestionJobIdentityDTO> tutorialUnits = attachmentVideoUnitRepository.findTutorialLectureUnitIdentities(censusUnitIds);
        if (orphans.isEmpty() && tutorialUnits.isEmpty()) {
            return;
        }
        log.info("Reconcile: deleting index rows for course {} of {} orphaned units {} and {} tutorial lecture units {}", census.courseId(), orphans.size(),
                orphans.stream().map(IngestionJobIdentityDTO::lectureUnitId).toList(), tutorialUnits.size(),
                tutorialUnits.stream().map(IngestionJobIdentityDTO::lectureUnitId).toList());
        irisLectureApi.get().deleteLectureUnitsByIdentity(Stream.concat(orphans.stream(), tutorialUnits.stream()).toList());
    }
}
