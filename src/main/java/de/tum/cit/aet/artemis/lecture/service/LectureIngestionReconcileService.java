package de.tum.cit.aet.artemis.lecture.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
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
 * exists in the database is an orphan and gets deleted.</li>
 * </ul>
 * The reconciler never certifies anything itself: every divergence is resolved by requeueing the unit
 * through the normal pipeline, whose end-of-run audit is the only thing that produces a confirmed
 * fingerprint. Re-runs of genuinely unchanged, complete content are cheap because the Iris pipeline
 * skips the expensive per-page work in that case.
 * <p>
 * The walk is budgeted and cursor-based: each pass covers the next few courses (including inactive and
 * archived ones the backfill never reaches) and requeues at most a configured number of units, so a large
 * backlog drains gradually underneath fresh uploads instead of flooding the queue. The cursor lives in
 * memory; the walker only runs on the scheduling node, and losing the cursor on a restart merely restarts
 * the walk from the beginning, which is idempotent.
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class LectureIngestionReconcileService {

    private static final Logger log = LoggerFactory.getLogger(LectureIngestionReconcileService.class);

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final LectureUnitContentFingerprintService contentFingerprintService;

    private final LectureContentProcessingService processingService;

    /**
     * Dispatch priority of reconcile requeues: behind fresh work (0) and behind nothing else,
     * so draining backlog and healing drift never starves a fresh upload.
     */
    static final int RECONCILE_DISPATCH_PRIORITY = 2;

    private final int coursesPerRun;

    private final int requeueLimitPerRun;

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
            "artemisApp.attachmentVideoUnit.processing.error.attachmentUnreadable");

    /**
     * How many times a FAILED unit is revived without an intervening successful completion before it is left
     * FAILED for manual attention. The backstop for a generic (unclassified) error that is really permanent:
     * with the ~hours-long cooldown this spans days of retrying, far longer than any transient outage, and the
     * count resets on a successful DONE, so it never shortens genuine transient recovery.
     */
    private static final int MAX_REVIVALS = 10;

    private final AtomicLong courseCursor = new AtomicLong(0);

    public LectureIngestionReconcileService(LectureUnitProcessingStateRepository processingStateRepository, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            Optional<IrisLectureApi> irisLectureApi, LectureUnitContentFingerprintService contentFingerprintService, LectureContentProcessingService processingService,
            @Value("${artemis.iris.ingestion.reconcile.courses-per-run:5}") int coursesPerRun,
            @Value("${artemis.iris.ingestion.reconcile.requeue-limit-per-run:10}") int requeueLimitPerRun,
            @Value("${artemis.iris.ingestion.reconcile.quality-threshold:0.8}") double qualityThreshold,
            @Value("${artemis.iris.ingestion.reconcile.failed-revival-cooldown:PT3H}") Duration failedRevivalCooldown) {
        this.processingStateRepository = processingStateRepository;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.irisLectureApi = irisLectureApi;
        this.contentFingerprintService = contentFingerprintService;
        this.processingService = processingService;
        this.coursesPerRun = coursesPerRun;
        this.requeueLimitPerRun = requeueLimitPerRun;
        this.qualityThreshold = qualityThreshold;
        this.failedRevivalCooldown = failedRevivalCooldown;
    }

    /**
     * Walk the next slice of courses and reconcile each one.
     * Called by the scheduler on the scheduling node; one call covers up to the configured number of
     * courses and spends at most the configured requeue budget across all of them.
     *
     * @return how many units were requeued or newly triggered; the caller dispatches when this is positive
     */
    public int walkNextCourses() {
        if (irisLectureApi.isEmpty()) {
            return 0;
        }
        List<Long> courseIds = attachmentVideoUnitRepository.findCourseIdsWithAttachmentVideoUnitsAfter(courseCursor.get(), PageRequest.of(0, coursesPerRun));
        if (courseIds.isEmpty()) {
            log.debug("Ingestion reconcile walk completed a full pass, restarting from the beginning next run");
            courseCursor.set(0);
            return 0;
        }
        int spent = 0;
        for (Long courseId : courseIds) {
            int courseSpent = 0;
            // Isolate each course so one failing course (e.g. its Iris census call throwing) does not
            // abort the pass before the cursor advances, which would re-hit the same course every run
            // and permanently block reconciliation of every course after it.
            try {
                courseSpent = reconcileCourse(courseId, requeueLimitPerRun - spent);
            }
            catch (RuntimeException e) {
                log.error("Reconcile: course {} failed this pass and was skipped: {}", courseId, e.getMessage());
            }
            spent += courseSpent;
            if (courseSpent > 0) {
                log.info("reconcile-course course={} requeued_or_triggered={}", courseId, courseSpent);
            }
            courseCursor.set(courseId);
            if (spent >= requeueLimitPerRun) {
                // Budget exhausted mid-slice; the cursor stays on this course's predecessor-inclusive
                // position, and anything unvisited is caught on the next full pass. A saturated budget
                // is also the mass-divergence signal: a backup restore or collection recreate makes
                // every walked course diverge at once, and healing at this budget takes many passes.
                log.error(
                        "mass-divergence: reconcile budget of {} exhausted after {} of {} courses — "
                                + "the index diverges broadly (backup restore or collection recreate?); healing continues at the configured pace",
                        requeueLimitPerRun, courseIds.indexOf(courseId) + 1, courseIds.size());
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
        IngestionCensusDTO census = irisLectureApi.get().getIngestionCensus(courseId);
        boolean censusAvailable = census != null;
        Map<Long, IngestionCensusUnitDTO> censusByUnitId = censusAvailable
                ? census.units().stream().collect(Collectors.toMap(IngestionCensusUnitDTO::lectureUnitId, Function.identity(), (first, second) -> first))
                : Map.of();

        List<AttachmentVideoUnit> units = attachmentVideoUnitRepository.findAllWithAttachmentByCourseId(courseId);
        Map<Long, LectureUnitProcessingState> stateByUnitId = processingStateRepository.findWithLectureUnitByCourseId(courseId).stream()
                .collect(Collectors.toMap(state -> state.getLectureUnit().getId(), Function.identity(), (first, second) -> first));

        int spent = 0;
        for (AttachmentVideoUnit unit : units) {
            if (spent >= requeueBudget) {
                break;
            }
            // Isolate each unit: an unexpected failure on one (a malformed link, a bad census entry)
            // must not abort the course, which would otherwise leave the cursor stuck and starve every
            // later course of reconciliation.
            try {
                boolean hasVideo = unit.getVideoSource() != null && !unit.getVideoSource().isBlank();
                boolean hasPdf = unit.getAttachment() != null && unit.getAttachment().getLink() != null && unit.getAttachment().getLink().endsWith(".pdf");
                if (!hasVideo && !hasPdf) {
                    continue;
                }

                LectureUnitProcessingState state = stateByUnitId.get(unit.getId());
                if (state == null) {
                    // Never entered the pipeline: typically a pre-pipeline unit in an inactive or archived
                    // course, which the active-course backfill deliberately does not reach.
                    log.info("Reconcile: triggering processing for unit {} of course {} (no processing state)", unit.getId(), courseId);
                    processingService.triggerProcessingAsBacklog(unit);
                    spent++;
                    continue;
                }
                spent += switch (state.getPhase()) {
                    case DONE -> reconcileDoneUnit(unit, state, census, censusByUnitId.get(unit.getId()));
                    case SKIPPED -> reconcileSkippedUnit(unit, state);
                    // A FAILED unit is revived once its transient cause has had time to clear (see
                    // reconcileFailedUnit); only permanent content failures stay terminal for the manual button.
                    case FAILED -> reconcileFailedUnit(state, censusAvailable);
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
        return spent;
    }

    /**
     * Reset a state so its next dispatch is a full re-ingest rather than a skip: clear the confirmed
     * fingerprint and set the force-reingest flag. Every reconcile re-queue that heals a divergence uses
     * this, because without forcing the re-run the pipeline's skip-check would treat the unit as complete
     * and skip — so the re-queue would loop forever without ever rewriting the unit. Forcing guarantees the
     * unit is deleted and rewritten to a single clean generation, which converges and stops the re-queue.
     *
     * @param fresh the freshly-loaded state to mutate inside the requeue's optimistic update
     */
    private static void forceRebuild(LectureUnitProcessingState fresh) {
        fresh.setConfirmedFingerprint(null);
        fresh.setForceReingest(true);
    }

    /**
     * Reconcile a DONE unit against its current content and the index reality.
     */
    private int reconcileDoneUnit(AttachmentVideoUnit unit, LectureUnitProcessingState state, @Nullable IngestionCensusDTO census, @Nullable IngestionCensusUnitDTO censusEntry) {
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
            // Legacy row that predates verification, or the content changed without the update path firing.
            requeueForReconcile(state, observedFingerprint, "no confirmed fingerprint for the current content", fresh -> {
            });
            return 1;
        }
        if (census != null) {
            if (censusEntry == null || censusEntry.unitRowCount() == 0 || !currentFingerprint.equals(censusEntry.contentFingerprint())) {
                // The run was confirmed, but the index no longer holds a matching stamp: the data was lost
                // or replaced after the fact (backup restore, collection recreate, raced delete). Force a
                // full re-ingest so the run rewrites the unit instead of the skip-check treating it as done.
                requeueForReconcile(state, observedFingerprint, "index stamp missing or different from the confirmed fingerprint", fresh -> forceRebuild(fresh));
                return 1;
            }
            // Re-queue on any structural divergence between what the index holds and what a complete unit
            // must hold. Every signal below is healed by a forced full re-ingest and then reported clean, so
            // the re-queue converges and stops rather than looping: generations counted after excluding inert
            // object-store ghosts (so a ghost-carrying unit is not re-queued forever), rows lost below the
            // certified count, a page-coverage hole against the PDF, a PDF unit left with no chunks, duplicate
            // unit rows, missing slide segments, and legacy null display numbers. A benign single-generation
            // count difference is deliberately not a signal, so a stale expectation alone does not cause churn.
            boolean hasPdf = unit.getAttachment() != null && unit.getAttachment().getLink() != null && unit.getAttachment().getLink().endsWith(".pdf");
            String divergence = divergenceReason(censusEntry, hasPdf);
            if (divergence != null) {
                requeueForReconcile(state, observedFingerprint, divergence, fresh -> forceRebuild(fresh));
                return 1;
            }
            if (isQualityRequeueDue(state, census, censusEntry)) {
                // A newer pipeline version can improve this low-quality unit. The versioned edge
                // (at most one quality requeue per pipeline version) is what makes this terminate;
                // the forced re-run keeps the stored generation when it does not score better.
                requeueForReconcile(state, observedFingerprint,
                        "quality " + censusEntry.qualityScore() + " below threshold and pipeline version " + census.currentPipelineVersion() + " is available", fresh -> {
                            fresh.setLastQualityPipelineVersion(census.currentPipelineVersion());
                            fresh.setForceReingest(true);
                        });
                return 1;
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
    private static String divergenceReason(IngestionCensusUnitDTO censusEntry, boolean hasPdf) {
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
        requeueForReconcile(state, state.getConfirmedFingerprint(), "unit became processable after being skipped", fresh -> {
        });
        return 1;
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
     * Revivals are bounded, but generously: after {@link #MAX_REVIVALS} revivals without an intervening
     * successful completion the unit is left FAILED for the manual retry button instead of being re-attempted
     * forever. This is the backstop for a genuinely-unprocessable unit that reports a generic (unclassified)
     * error and so is not in {@link #PERMANENT_ERROR_KEYS} — without it, such a unit would revive every
     * cooldown indefinitely. The cap does not shorten recovery from a real outage: a store that is down does
     * not answer the census, so those passes neither revive nor count (only revivals dispatched into a
     * healthy store that still fail are counted), and the count resets on a successful DONE, so a transient
     * failure that later succeeds regains a full budget. The requeue itself reuses {@link #requeueForReconcile},
     * whose re-fetch guard makes it a no-op if the row changed since the batch read; passing the unit's own
     * confirmed fingerprint makes that guard neutral.
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
        if (state.getRevivalCount() >= MAX_REVIVALS) {
            // Exhausted its revival budget without ever succeeding: treat a generic error that keeps
            // recurring as effectively permanent and leave it FAILED for manual attention rather than
            // re-attempting it forever.
            return 0;
        }
        if (state.getLastUpdated() == null || state.getLastUpdated().isAfter(ZonedDateTime.now().minus(failedRevivalCooldown))) {
            return 0;
        }
        requeueForReconcile(state, state.getConfirmedFingerprint(), "transient failure cooled down, reviving for another attempt",
                LectureUnitProcessingState::incrementRevivalCount);
        return 1;
    }

    private void requeueForReconcile(LectureUnitProcessingState state, String observedFingerprint, String reason, Consumer<LectureUnitProcessingState> applyIntent) {
        long unitId = state.getLectureUnit().getId();
        ProcessingPhase decidedPhase = state.getPhase();
        Optional<LectureUnitProcessingState> currentState = processingStateRepository.findById(state.getId());
        if (currentState.isEmpty() || currentState.get().getPhase() != decidedPhase || !Objects.equals(currentState.get().getConfirmedFingerprint(), observedFingerprint)) {
            log.debug("Reconcile: skipping requeue of unit {} — its state changed since the batch read", unitId);
            return;
        }
        LectureUnitProcessingState fresh = currentState.get();
        log.info("Reconcile: requeueing unit {} ({})", unitId, reason);
        applyIntent.accept(fresh);
        fresh.resetRetryCount();
        fresh.requeue();
        fresh.setDispatchPriority(RECONCILE_DISPATCH_PRIORITY);
        processingStateRepository.save(fresh);
    }

    /**
     * Delete index rows whose lecture unit no longer exists in the database.
     * The census reports a unit as long as ANY collection still holds rows for it, so this is what
     * garbage-collects leftovers from failed deletions and superseded runs.
     */
    private void deleteOrphanedIndexRows(IngestionCensusDTO census, List<AttachmentVideoUnit> courseUnits) {
        // The course unit list excludes tutorial lectures, so existence is checked against the database
        // rather than against the list: an id that exists at all is not an orphan. One batched query
        // resolves all census ids at once instead of an existsById per row.
        List<Long> censusUnitIds = census.units().stream().map(IngestionCensusUnitDTO::lectureUnitId).toList();
        Set<Long> existingUnitIds = censusUnitIds.isEmpty() ? Set.of() : attachmentVideoUnitRepository.findExistingIds(censusUnitIds);
        List<IngestionJobIdentityDTO> orphans = census.units().stream().filter(entry -> !existingUnitIds.contains(entry.lectureUnitId()))
                .map(entry -> new IngestionJobIdentityDTO(census.courseId(), entry.lectureId() != null ? entry.lectureId() : 0, entry.lectureUnitId())).toList();
        if (orphans.isEmpty()) {
            return;
        }
        log.info("Reconcile: deleting {} orphaned index rows for course {} (units {})", orphans.size(), census.courseId(),
                orphans.stream().map(IngestionJobIdentityDTO::lectureUnitId).toList());
        irisLectureApi.get().deleteLectureUnitsByIdentity(orphans);
    }

    /**
     * Resolve a stuck INGESTING run that very likely completed on the Iris side with only its terminal
     * callback lost, without charging the retry budget.
     * <p>
     * Evidence: the census stamp of the unit equals the fingerprint this run dispatched with. The stamp
     * is written by the run's final vector store write, so its presence means the pipeline reached its
     * last stage. The state is requeued rather than marked DONE, because only a terminal success callback
     * (behind the Iris end-of-run audit) may confirm a fingerprint; the re-run is cheap for content that
     * is already current and complete. What this avoids is the failure path: without it, a lost success
     * callback costs a retry, and enough lost callbacks in a row mark a perfectly ingested unit as
     * permanently FAILED.
     *
     * @param state the stuck processing state, in phase INGESTING
     * @return true if the state was requeued without retry penalty; false if the normal failure handling should proceed
     */
    public boolean resolveStuckIngestionWithoutRetryPenalty(LectureUnitProcessingState state) {
        if (irisLectureApi.isEmpty() || state.getPhase() != ProcessingPhase.INGESTING || state.getContentFingerprint() == null) {
            return false;
        }
        long unitId = state.getLectureUnit().getId();
        long courseId = state.getLectureUnit().getLecture().getCourse().getId();
        IngestionCensusDTO census = irisLectureApi.get().getIngestionCensus(courseId);
        if (census == null) {
            return false;
        }
        IngestionCensusUnitDTO entry = census.units().stream().filter(unit -> unit.lectureUnitId() == unitId).findFirst().orElse(null);
        if (entry == null || entry.unitRowCount() == 0 || !state.getContentFingerprint().equals(entry.contentFingerprint())) {
            return false;
        }
        log.warn("Reconcile: unit {} looks fully ingested but its terminal callback never arrived; requeueing without retry penalty", unitId);
        state.requeue();
        processingStateRepository.save(state);
        return true;
    }
}
