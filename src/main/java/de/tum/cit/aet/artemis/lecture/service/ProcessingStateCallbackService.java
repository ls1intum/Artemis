package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegmentConverter;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Service that handles callbacks, capacity-aware dispatch, and state transitions for the lecture content processing pipeline.
 * <p>
 * The {@code lecture_unit_processing_state} table acts as a database-backed job queue, dispatched via conditional
 * atomic UPDATEs (see {@link LectureUnitProcessingStateRepository#claimIdleForDispatch}) rather than row locks, for
 * safe concurrent dispatch in clustered Artemis deployments.
 * <p>
 * This service handles:
 * <ul>
 * <li>Capacity-aware dispatch: claiming IDLE jobs and sending them to Iris when slots are available</li>
 * <li>Checkpoint callbacks: saving transcription data from Iris and transitioning TRANSCRIBING → INGESTING</li>
 * <li>Ingestion completion callbacks from Iris webhooks</li>
 * <li>Failure handling with retry logic (reset to IDLE for re-dispatch)</li>
 * </ul>
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class ProcessingStateCallbackService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingStateCallbackService.class);

    /**
     * Maximum number of concurrent processing jobs (TRANSCRIBING or INGESTING).
     * Prevents overwhelming Iris with too many simultaneous jobs.
     * Configurable via {@code artemis.iris.ingestion.max-concurrent-jobs}; defaults to 2.
     */
    private final int maxConcurrentJobs;

    /**
     * How long a retry claim keeps a row out of the candidate list. It has to outlast the dispatch the claim belongs
     * to, and it doubles as the recovery window: a node killed mid-dispatch leaves the claim in place until it lapses,
     * after which the row is eligible again. Intended to match the scheduler's no-callback timeout
     * ({@code artemis.iris.ingestion.no-callback-timeout-minutes}), which is the point at which a dispatch is no
     * longer considered in flight — keep the two in sync if either is overridden.
     */
    private final int retryClaimLeaseMinutes;

    /**
     * Name of the cluster-wide lock that serializes push dispatch, so that count + claim + send is atomic across
     * all nodes and two nodes cannot both fill the same free capacity.
     */
    private static final String DISPATCH_LOCK_NAME = "lecture-ingestion-dispatch";

    private static final JsonMapper objectMapper = JsonObjectMapper.get();

    private static final String PROCESSING_STATE_TOPIC = "/topic/lectures/%d/unit-processing-state";

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    private final AttachmentRepository attachmentRepository;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final WebsocketMessagingService websocketMessagingService;

    private final LectureUnitContentFingerprintService contentFingerprintService;

    private final DistributedDataProvider distributedDataProvider;

    private final FeatureToggleService featureToggleService;

    /**
     * How long after the last claim or heartbeat call a pulling Pyris worker still counts as present.
     * While a worker is present, the legacy push dispatch is suppressed and IDLE jobs simply wait in
     * the queue for the next claim; when the worker disappears past this grace (an old Iris without
     * worker support, or the worker gone for good), push dispatch resumes automatically. Sized as a
     * generous multiple of the worker's heartbeat interval so one lost heartbeat never flips modes.
     */
    private final Duration workerModeGrace;

    /** Upper bound on jobs handed out per single worker claim call, purely as a sanity clamp. */
    private final int maxJobsPerClaim;

    private final IrisLectureUnitSyncStateRepository irisLectureUnitSyncStateRepository;

    private static final String WORKER_MAP_NAME = "pyris-ingestion-worker";

    private static final String WORKER_LAST_SEEN_KEY = "lastSeenAt";

    @Nullable
    private DistributedMap<String, String> workerMap;

    public ProcessingStateCallbackService(LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository,
            AttachmentRepository attachmentRepository, Optional<IrisLectureApi> irisLectureApi, WebsocketMessagingService websocketMessagingService,
            LectureUnitContentFingerprintService contentFingerprintService, DistributedDataProvider distributedDataProvider, FeatureToggleService featureToggleService,
            @Value("${artemis.iris.ingestion.max-concurrent-jobs:2}") int maxConcurrentJobs,
            @Value("${artemis.iris.ingestion.retry-claim-lease-minutes:20}") int retryClaimLeaseMinutes,
            @Value("${artemis.iris.ingestion.worker-mode-grace:PT90S}") Duration workerModeGrace, @Value("${artemis.iris.ingestion.max-jobs-per-claim:8}") int maxJobsPerClaim,
            IrisLectureUnitSyncStateRepository irisLectureUnitSyncStateRepository) {
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.attachmentRepository = attachmentRepository;
        this.irisLectureApi = irisLectureApi;
        this.websocketMessagingService = websocketMessagingService;
        this.contentFingerprintService = contentFingerprintService;
        this.distributedDataProvider = distributedDataProvider;
        this.featureToggleService = featureToggleService;
        this.maxConcurrentJobs = maxConcurrentJobs;
        this.retryClaimLeaseMinutes = retryClaimLeaseMinutes;
        this.workerModeGrace = workerModeGrace;
        this.maxJobsPerClaim = maxJobsPerClaim;
        this.irisLectureUnitSyncStateRepository = irisLectureUnitSyncStateRepository;
    }

    private DistributedMap<String, String> getWorkerMap() {
        if (workerMap == null) {
            workerMap = distributedDataProvider.getMap(WORKER_MAP_NAME);
        }
        return workerMap;
    }

    /**
     * The configured maximum number of concurrent processing jobs.
     * Exposed for the scheduler, whose backfill applies the same cap.
     *
     * @return the maximum number of jobs that may be TRANSCRIBING or INGESTING at once
     */
    int getMaxConcurrentJobs() {
        return maxConcurrentJobs;
    }

    /**
     * Returns a synchronization state to the retry pass now that Pyris holds the lecture unit: a row settled as
     * {@link IrisLectureUnitSyncState#STATUS_NOT_INGESTED} or {@link IrisLectureUnitSyncState#STATUS_FAILED} is skipped by the retry query and not recreated by the backfill, so
     * ingestion completing is what reopens it; a row that is {@link IrisLectureUnitSyncState#STATUS_IN_PROGRESS} is reopened too, since its in-flight request can still answer
     * "not ingested" and reopening tells the listener that answer no longer holds.
     *
     * @param state the current synchronization state of the lecture unit
     */
    private static void reopenSynchronization(IrisLectureUnitSyncState state) {
        boolean reopenable = IrisLectureUnitSyncState.STATUS_NOT_INGESTED.equals(state.getStatus()) || IrisLectureUnitSyncState.STATUS_FAILED.equals(state.getStatus())
                || IrisLectureUnitSyncState.STATUS_IN_PROGRESS.equals(state.getStatus());
        if (!reopenable) {
            return;
        }
        state.setStatus(IrisLectureUnitSyncState.STATUS_DIRTY);
        state.setRetryCount(0);
        state.setLastErrorKey(null);
        state.setNextRetryAt(ZonedDateTime.now());
    }

    // -------------------- Capacity-Aware Dispatch --------------------

    /**
     * Dispatch pending IDLE jobs to Iris, respecting capacity limits.
     * <p>
     * Claim-commit-then-send: the repository atomically claims rows (marking {@code startedAt}) in its own committed transaction before any HTTP request leaves this node, so a
     * crash between claim and send never spawns a duplicate pipeline — it just leaves a claimed row the scheduler's claim-expiry release requeues, with no database row locks
     * held across the calls to Pyris. Claim order implements queue priority: fresh work first, retries second, backlog last. Called from
     * {@link LectureContentProcessingService#triggerProcessing} (right after creating IDLE state), {@link #handleIngestionComplete} (filling a freed slot), and
     * {@link LectureContentProcessingScheduler#processScheduledRetries} (periodic backup every 5 minutes).
     * <p>
     * Cluster safety comes from the conditional claim on each candidate, not a transaction spanning the read and write (see
     * {@link LectureUnitProcessingStateRepository#claimIdleForDispatch}); the cluster-wide dispatch lock serializes the capacity check with the claims so two nodes cannot both
     * see the same free slots and together exceed the configured maximum.
     */
    public void dispatchPendingJobs() {
        if (irisLectureApi.isEmpty()) {
            log.debug("Iris API not available, skipping dispatch");
            return;
        }

        // Pull mode: a worker claims its own capacity, so jobs wait as IDLE for its next claim; the push below survives as the fallback for an Iris without worker support.
        if (isWorkerModeActive()) {
            log.debug("Pyris worker active, leaving pending jobs for pull-based claim");
            return;
        }

        // Serialize dispatch cluster-wide: without this lock, concurrent callers can each see the same activeCount and together exceed the max.
        DistributedLock dispatchLock = distributedDataProvider.getLock(DISPATCH_LOCK_NAME);
        dispatchLock.lock();
        try {
            long activeCount = processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING));
            int availableSlots = (int) (maxConcurrentJobs - activeCount);

            if (availableSlots <= 0) {
                log.debug("No available slots for dispatch ({} active, max {})", activeCount, maxConcurrentJobs);
                return;
            }

            ZonedDateTime now = ZonedDateTime.now();

            List<LectureUnitProcessingState> retryJobs = processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), now, availableSlots);

            for (LectureUnitProcessingState state : retryJobs) {
                if (availableSlots <= 0) {
                    break;
                }
                ZonedDateTime leaseExpiry = now.plusMinutes(retryClaimLeaseMinutes);
                if (processingStateRepository.claimRetryEligible(state.getId(), now, leaseExpiry) == 0) {
                    log.debug("Another node claimed the retry of unit {}", state.getLectureUnit().getId());
                    continue;
                }
                log.info("Re-dispatching retry-eligible unit {} (attempt {}/{})", state.getLectureUnit().getId(), state.getRetryCount(), MAX_PROCESSING_RETRIES);
                // Mirror the claim onto the loaded entity, saved again further down: writing back the stale
                // value would re-list this row. A failed dispatch leaves the lease, so the claim lapses on its own.
                state.setRetryEligibleAt(leaseExpiry);
                // Isolate each dispatch: one claimed unit's failure must not abort the remaining claims.
                try {
                    dispatchSingleJob(state);
                }
                catch (Exception e) {
                    log.error("Unexpected failure dispatching unit {}, marking as failed: {}", state.getLectureUnit() != null ? state.getLectureUnit().getId() : "null",
                            e.getMessage());
                    handleProcessingFailure(state);
                }
                availableSlots--;
            }

            List<LectureUnitProcessingState> idleJobs = processingStateRepository.findIdleForDispatch(now, availableSlots);

            if (idleJobs.isEmpty() && retryJobs.isEmpty()) {
                log.debug("No jobs ready for dispatch");
                return;
            }
            if (!idleJobs.isEmpty()) {
                log.info("Dispatching {} IDLE jobs to Iris ({} slots available)", idleJobs.size(), availableSlots);
            }

            for (LectureUnitProcessingState state : idleJobs) {
                if (processingStateRepository.claimIdleForDispatch(state.getId(), now) == 0) {
                    log.debug("Another node claimed the dispatch of unit {}", state.getLectureUnit().getId());
                    continue;
                }
                // Mirror the claim onto the loaded entity, for the reason given on the retry loop above.
                state.setStartedAt(now);
                // Isolate each dispatch: one bad unit must not strand the rest of this pass's claims.
                try {
                    dispatchSingleJob(state);
                }
                catch (Exception e) {
                    log.error("Unexpected failure dispatching unit {}, marking as failed: {}", state.getLectureUnit() != null ? state.getLectureUnit().getId() : "null",
                            e.getMessage());
                    handleProcessingFailure(state);
                }
            }
        }
        finally {
            dispatchLock.unlock();
        }
    }

    /** Dispatch a single IDLE job to Iris, starting as TRANSCRIBING or INGESTING based on existing transcription data. */
    private void dispatchSingleJob(LectureUnitProcessingState state) {
        PreparedDispatch prepared = prepareClaimedState(state);
        if (prepared == null) {
            return;
        }
        AttachmentVideoUnit attachmentUnit = prepared.unit();
        LectureUnit unit = attachmentUnit;
        ProcessingPhase targetPhase = prepared.targetPhase();
        String contentFingerprint = prepared.contentFingerprint();

        try {
            String jobToken = irisLectureApi.get().addLectureUnitToPyrisDB(attachmentUnit, contentFingerprint, state.isForceReingest());

            if (jobToken == null) {
                log.info("Processing not applicable for unit {} (course settings or content type), marking as SKIPPED", unit.getId());
                state.transitionTo(ProcessingPhase.SKIPPED);
                processingStateRepository.save(state);
                return;
            }

            state.transitionTo(targetPhase);
            state.setIngestionJobToken(jobToken);
            state.setContentFingerprint(contentFingerprint);
            processingStateRepository.save(state);
            log.info("Dispatched unit {} as {} with token {}", unit.getId(), targetPhase, maskToken(jobToken));

            TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(unit.getId()).map(LectureTranscription::getTranscriptionStatus).orElse(null);
            notifyProcessingStateChange(state, txStatus);
        }
        catch (Exception e) {
            log.error("Failed to dispatch unit {} to Iris: {}", unit.getId(), e.getMessage());
            handleProcessingFailure(state);
        }
    }

    /**
     * The dispatch-ready description of a claimed IDLE state, shared by both transports: the legacy
     * push posts it to Iris, the pull-based worker claim returns it to the worker.
     */
    private record PreparedDispatch(LectureUnitProcessingState state, AttachmentVideoUnit unit, ProcessingPhase targetPhase, String contentFingerprint) {
    }

    /**
     * Run the transport-independent front half of a dispatch on a claimed state: unit type check,
     * target phase determination, and content fingerprinting. States that cannot be dispatched are
     * terminally handled here (FAILED with a specific key) and reported as {@code null}.
     *
     * @param state a state freshly claimed for dispatch
     * @return the prepared dispatch, or {@code null} when the state was failed here instead
     */
    @Nullable
    private PreparedDispatch prepareClaimedState(LectureUnitProcessingState state) {
        LectureUnit unit = state.getLectureUnit();
        if (!(unit instanceof AttachmentVideoUnit attachmentUnit)) {
            log.warn("Cannot dispatch non-AttachmentVideoUnit (id={})", unit != null ? unit.getId() : "null");
            state.markFailed("artemisApp.attachmentVideoUnit.processing.error.invalidUnitType");
            processingStateRepository.save(state);
            return null;
        }

        boolean hasVideo = attachmentUnit.getVideoSource() != null && !attachmentUnit.getVideoSource().isBlank();

        // Check if transcription already completed (e.g., retry after ingestion failure)
        Optional<LectureTranscription> existingTranscription = transcriptionRepository.findByLectureUnit_Id(unit.getId());
        boolean hasCompletedTranscription = existingTranscription.isPresent() && existingTranscription.get().getTranscriptionStatus() == TranscriptionStatus.COMPLETED;

        ProcessingPhase targetPhase = hasVideo && !hasCompletedTranscription ? ProcessingPhase.TRANSCRIBING : ProcessingPhase.INGESTING;

        String contentFingerprint;
        try {
            contentFingerprint = contentFingerprintService.computeFingerprint(attachmentUnit);
        }
        catch (RuntimeException e) {
            if (FilePathConverter.getFileUploadPath() == null) {
                // Startup raced this claim before FilePathConverter was configured (transient, not unreadable),
                // so requeue for re-dispatch rather than failing a healthy unit with a key the reconciler never revives.
                log.warn("File store not initialized yet; requeuing unit {} for re-dispatch instead of failing it", unit.getId());
                state.requeue();
                processingStateRepository.save(state);
                return null;
            }
            // A genuinely unreadable file or malformed link is a local problem Pyris cannot fix, so retrying would
            // burn the retry budget uselessly — fail with a specific key instead of aborting the whole claimed batch.
            log.error("Cannot read attachment for unit {}, marking as FAILED without dispatch: {}", unit.getId(), e.getMessage());
            state.markFailed("artemisApp.attachmentVideoUnit.processing.error.attachmentUnreadable");
            processingStateRepository.save(state);
            notifyProcessingStateChange(state, null);
            return null;
        }
        return new PreparedDispatch(state, attachmentUnit, targetPhase, contentFingerprint);
    }

    // -------------------- Pull-Based Worker Dispatch --------------------

    /**
     * Claim up to {@code maxJobs} pending jobs for a pulling Pyris worker: expired retries first, then IDLE work in priority order (fresh uploads before backlog), the same
     * order as the push path. Each candidate goes through the same per-row conditional claims as the push path, so no row locks span the loop. A claim whose activation never
     * arrives (worker died between claim and execution) recovers on its own: an IDLE claim is released by the abandoned-claim sweep, a retry claim lapses with its lease. No
     * capacity check happens here — in pull mode capacity belongs to the worker, which only claims what it can run.
     *
     * @param workerBootId boot id of the claiming Pyris worker process
     * @param maxJobs      how many jobs the worker can take right now
     * @return the claimed units, described by scalars for the iris module to prepare and activate
     */
    public List<ClaimedIngestionUnitDTO> claimUnitsForWorker(String workerBootId, int maxJobs) {
        markWorkerSeen(workerBootId);
        if (!featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)) {
            // The toggle is the operator's kill switch for new work; the push path, retries, backfill and reconcile
            // all honor it, so a pulling worker must not become a way around it.
            log.debug("LectureContentProcessing feature is disabled, handing out no jobs to worker {}", workerBootId);
            return List.of();
        }
        int jobs = Math.clamp(maxJobs, 0, maxJobsPerClaim);
        if (jobs == 0) {
            return List.of();
        }
        // Truncated to whole seconds: written here and later compared for exact equality by
        // activateClaimedJob/markSkippedIfStillClaimed, and MySQL's default DATETIME column rounds
        // to whole-second precision on write — an untruncated value here would never match what comes
        // back out, failing the exact-match guard on every claim.
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<LectureUnitProcessingState> claimed = new ArrayList<>();

        for (LectureUnitProcessingState state : processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), now, jobs)) {
            ZonedDateTime leaseExpiry = now.plusMinutes(retryClaimLeaseMinutes);
            if (processingStateRepository.claimRetryEligible(state.getId(), now, leaseExpiry) == 0) {
                continue;
            }
            // Mirror the claim onto the loaded entity so a later save cannot write back the stale value.
            state.setRetryEligibleAt(leaseExpiry);
            log.info("Worker re-claiming retry-eligible unit {} (attempt {}/{})", state.getLectureUnit().getId(), state.getRetryCount(), MAX_PROCESSING_RETRIES);
            claimed.add(state);
        }

        int remaining = jobs - claimed.size();
        if (remaining > 0) {
            for (LectureUnitProcessingState state : processingStateRepository.findIdleForDispatch(now, remaining)) {
                if (processingStateRepository.claimIdleForDispatch(state.getId(), now) == 0) {
                    continue;
                }
                state.setStartedAt(now);
                claimed.add(state);
            }
        }

        List<ClaimedIngestionUnitDTO> result = new ArrayList<>();
        for (LectureUnitProcessingState state : claimed) {
            PreparedDispatch prepared = prepareClaimedState(state);
            if (prepared != null) {
                // Exactly one of these is set at claim time: startedAt for an IDLE claim, retryEligibleAt for a
                // retry claim (mirrors activateClaimedJob's own claim-shape check just above).
                ZonedDateTime claimedAt = state.getStartedAt() != null ? state.getStartedAt() : state.getRetryEligibleAt();
                result.add(new ClaimedIngestionUnitDTO(prepared.unit().getId(), prepared.contentFingerprint(), state.isForceReingest(), prepared.targetPhase(), claimedAt));
            }
        }
        if (!result.isEmpty()) {
            log.info("Worker {} claimed {} jobs", workerBootId, result.size());
        }
        return result;
    }

    /**
     * Activate a claim once the iris side registered the job token and handed the payload to the worker: transition into the target phase, record token/fingerprint, and open
     * the worker lease — from here the run is alive exactly as long as the worker keeps renewing it. Bound to the exact claim that produced it (matching {@code claimedAt}, not
     * just claim shape): an activation arriving after the claim was released and re-claimed by a newer, still-unactivated claim matches nothing instead of activating that
     * newer claim with this stale job token.
     *
     * @param lectureUnitId      the claimed unit
     * @param jobToken           the registered Pyris job token
     * @param targetPhase        the in-flight phase determined at claim time
     * @param contentFingerprint the fingerprint computed at claim time
     * @param workerBootId       boot id of the worker executing the run
     * @param claimedAt          the claim marker observed at claim time, from {@link ClaimedIngestionUnitDTO#claimedAt()}
     * @return whether the claim was activated; false when the unit no longer holds this exact claim
     */
    public boolean activateClaimedJob(long lectureUnitId, String jobToken, ProcessingPhase targetPhase, String contentFingerprint, String workerBootId, ZonedDateTime claimedAt) {
        int activated = processingStateRepository.activateClaimedJob(lectureUnitId, targetPhase, jobToken, contentFingerprint, workerBootId, claimedAt, ZonedDateTime.now());
        if (activated == 0) {
            log.warn("Ignoring activation of unit {} by worker {}: the unit no longer holds the claim (released, re-claimed, or already activated)", lectureUnitId, workerBootId);
            return false;
        }
        log.info("Worker {} activated unit {} as {} with token {}", workerBootId, lectureUnitId, targetPhase, maskToken(jobToken));

        processingStateRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(state -> {
            TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).map(LectureTranscription::getTranscriptionStatus).orElse(null);
            notifyProcessingStateChange(state, txStatus);
        });
        return true;
    }

    /**
     * Mark a claimed unit SKIPPED (not processable), bound like {@link #activateClaimedJob} to the claim that
     * produced it: a stale result from a lapsed, re-claimed claim matches nothing instead of cancelling the newer run.
     *
     * @param lectureUnitId the claimed unit
     * @param claimedAt     the claim marker from {@link de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO#claimedAt()}
     * @return true when marked SKIPPED, false when the claim was no longer current
     */
    public boolean markClaimedUnitSkipped(long lectureUnitId, ZonedDateTime claimedAt) {
        int updated = processingStateRepository.markSkippedIfStillClaimed(lectureUnitId, claimedAt, ZonedDateTime.now());
        if (updated == 0) {
            log.info("Not marking unit {} SKIPPED: its claim is no longer current (released, re-claimed, or already activated)", lectureUnitId);
            return false;
        }
        log.info("Processing not applicable for claimed unit {} (course settings or content type), marking as SKIPPED", lectureUnitId);
        return true;
    }

    /**
     * Renew the worker lease of every listed run and report back the tokens Artemis no longer recognizes as
     * in flight, so the worker can stop executing runs whose lease was reclaimed. Each renewal is also pushed
     * to the client: the badge derives liveness from renewal freshness, so a stopped stream visibly loses contact.
     *
     * @param workerBootId    boot id of the heartbeating worker process
     * @param activeJobTokens the job tokens of every run the worker is currently executing
     * @return the subset of tokens that no longer belong to an in-flight run
     */
    public List<String> renewWorkerLeases(String workerBootId, List<String> activeJobTokens) {
        markWorkerSeen(workerBootId);
        List<String> revoked = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        for (String token : activeJobTokens) {
            Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByIngestionJobToken(token);
            if (stateOpt.isEmpty() || !stateOpt.get().isProcessing()) {
                revoked.add(token);
                continue;
            }
            LectureUnitProcessingState state = stateOpt.get();
            int updated = processingStateRepository.renewLease(state.getId(), token, now, workerBootId);
            if (updated == 0) {
                // A terminal callback cleared the token first; report revoked, not stale reality.
                revoked.add(token);
                continue;
            }
            // Reflects the just-persisted renewal for the notification only; never saved itself.
            state.renewLease(workerBootId);
            TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(state.getLectureUnit().getId()).map(LectureTranscription::getTranscriptionStatus)
                    .orElse(null);
            notifyProcessingStateChange(state, txStatus);
        }
        if (!revoked.isEmpty()) {
            log.warn("Worker {} heartbeat listed {} run(s) Artemis no longer tracks, reporting them revoked", workerBootId, revoked.size());
        }
        return revoked;
    }

    private void markWorkerSeen(String workerBootId) {
        getWorkerMap().put(WORKER_LAST_SEEN_KEY, Instant.now().toString());
        getWorkerMap().put("bootId", workerBootId);
    }

    /**
     * Whether a pulling Pyris worker has claimed or heartbeated within {@link #workerModeGrace}.
     */
    boolean isWorkerModeActive() {
        String lastSeen = getWorkerMap().get(WORKER_LAST_SEEN_KEY);
        if (lastSeen == null) {
            return false;
        }
        try {
            return Instant.parse(lastSeen).isAfter(Instant.now().minus(workerModeGrace));
        }
        catch (DateTimeParseException e) {
            return false;
        }
    }

    // -------------------- Callback Handlers --------------------

    /**
     * Called when the entire processing pipeline completes (from the Iris webhook callback). Validates the job token to reject stale callbacks from old jobs, and after
     * completion dispatches the next pending job to fill the freed slot.
     *
     * @param lectureUnitId      the ID of the lecture unit
     * @param jobToken           the job token from the callback
     * @param success            whether processing succeeded
     * @param errorCode          machine-readable error code (e.g. {@code YOUTUBE_PRIVATE}); {@code null} on success or unknown failure
     * @param displayPageNumbers list of displayed page numbers indexed by slide number (0-based: index 0 = slide 1);
     *                               {@code null} if not applicable or unavailable
     */
    public void handleIngestionComplete(Long lectureUnitId, String jobToken, boolean success, @Nullable String errorCode, @Nullable List<Integer> displayPageNumbers) {
        Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByLectureUnit_Id(lectureUnitId);

        if (stateOpt.isEmpty()) {
            log.warn("Received completion callback for unit {} but no processing state exists", lectureUnitId);
            return;
        }

        LectureUnitProcessingState state = stateOpt.get();

        // Validate token - reject stale callbacks from old jobs
        if (!Objects.equals(jobToken, state.getIngestionJobToken())) {
            log.info("Ignoring stale callback for unit {} (token mismatch: expected {}, got {})", lectureUnitId, maskToken(state.getIngestionJobToken()), maskToken(jobToken));
            return;
        }

        if (!state.isProcessing()) {
            log.warn("Received completion callback for unit {} in phase {} (expected TRANSCRIBING or INGESTING)", lectureUnitId, state.getPhase());
            return;
        }

        if (success) {
            // Atomic claim + terminal write in one statement: see completeIngestionIfLive.
            if (processingStateRepository.completeIngestionIfLive(state.getId(), jobToken, ZonedDateTime.now()) == 0) {
                log.info("Ignoring completion callback for unit {}: the run is no longer in flight under this token", lectureUnitId);
                return;
            }
            log.info("Processing completed successfully for unit {}", lectureUnitId);

            // Pyris now holds the unit, so a sync settled or in flight because it did not is worth retrying (the
            // transaction and lock come from the repository method, hence passing the transition into it). Runs
            // first and deliberately unguarded: a settled row is unreachable afterwards (no retry time, and the
            // backfill skips a unit that already has a row), so swallowing a failure here would strand the unit for
            // good — letting it propagate before anything else in this callback runs keeps that visible, not silent.
            irisLectureUnitSyncStateRepository.updateWithLectureUnitLock(lectureUnitId, ProcessingStateCallbackService::reopenSynchronization);

            // Written only once ownership is confirmed; see saveDisplayPageNumbers for the version guard.
            saveDisplayPageNumbers(state, displayPageNumbers);
            // Mirror the just-persisted transition for an accurate notification, without a second read.
            state.transitionTo(ProcessingPhase.DONE);
            state.setIngestionJobToken(null);
            state.setConfirmedFingerprint(state.getContentFingerprint());
            state.setForceReingest(null);

            TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).map(LectureTranscription::getTranscriptionStatus).orElse(null);
            notifyProcessingStateChange(state, txStatus);
        }
        else {
            log.warn("Processing failed for unit {} (errorCode={})", lectureUnitId, errorCode);
            // Same atomic-claim reasoning as the success branch, via failIfStillLive.
            if (!handleProcessingFailureIfStillLive(state, errorCode)) {
                log.info("Ignoring completion callback for unit {}: the run is no longer in flight under this token", lectureUnitId);
                return;
            }
        }

        dispatchPendingJobs();
    }

    /**
     * Handle checkpoint data from Iris callbacks (e.g., transcription results). Iris sends transcription data in the {@code result} field of status callbacks; this parses the
     * transcript JSON, saves it, and transitions TRANSCRIBING → INGESTING once the enriched transcript arrives. Checkpoint types, distinguished by segment content: raw (all
     * slideNumber=0) saved as PENDING, staying in TRANSCRIBING; enriched (some slideNumber≠0) saved as COMPLETED, transitioning to INGESTING.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token for validation
     * @param resultJson    the JSON string containing transcription data
     */
    public void handleCheckpointData(long lectureUnitId, String jobToken, String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return;
        }

        Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByLectureUnit_Id(lectureUnitId);
        if (stateOpt.isEmpty()) {
            log.warn("Received checkpoint for unit {} but no processing state exists", lectureUnitId);
            return;
        }

        LectureUnitProcessingState state = stateOpt.get();

        if (!Objects.equals(jobToken, state.getIngestionJobToken())) {
            log.info("Ignoring stale checkpoint for unit {} (token mismatch)", lectureUnitId);
            return;
        }

        if (state.getPhase() != ProcessingPhase.TRANSCRIBING) {
            log.debug("Ignoring checkpoint for unit {} in phase {} (expected TRANSCRIBING)", lectureUnitId, state.getPhase());
            return;
        }

        try {
            TranscriptionCheckpoint checkpoint = parseTranscriptionCheckpoint(resultJson);
            if (checkpoint == null) {
                return;
            }

            saveTranscription(lectureUnitId, state, checkpoint);
        }
        catch (JacksonException e) {
            log.warn("Failed to parse checkpoint data for unit {}: {}", lectureUnitId, e.getMessage());
        }
    }

    /**
     * Handle a heartbeat from a running Iris pipeline. Updates {@code lastUpdated} so stuck detection can use "time since last callback" instead of "time since phase started",
     * and records the optionally reported stage/progress so stalled runs (heartbeats without progress) become detectable. Called on every non-terminal callback that does NOT
     * carry checkpoint data.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token for validation
     * @param stageName     name of the stage the run is currently in; may be null (older Iris versions)
     * @param stageProgress progress counter within the stage; may be null
     * @param stageTotal    total work items of the stage; may be null
     */
    public void handleHeartbeat(long lectureUnitId, String jobToken, @Nullable String stageName, @Nullable Integer stageProgress, @Nullable Integer stageTotal) {
        Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByLectureUnit_Id(lectureUnitId);
        if (stateOpt.isEmpty()) {
            return;
        }

        LectureUnitProcessingState state = stateOpt.get();
        if (!Objects.equals(jobToken, state.getIngestionJobToken())) {
            return;
        }

        if (!state.isProcessing()) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        state.setLastUpdated(now);
        boolean stageAdvanced = state.recordStageProgress(stageName, stageProgress, stageTotal);
        // Conditional on token/phase: a terminal callback clearing the token first stops this from reviving a finished run.
        int applied = processingStateRepository.applyHeartbeat(state.getId(), jobToken, now, state.getCurrentStage(), state.getStageStartedAt(), state.getStageProgress(),
                state.getStageTotal(), state.getLastProgressAt());
        if (applied == 0) {
            log.debug("Ignoring heartbeat for unit {}: the run is no longer in flight under this token", lectureUnitId);
            return;
        }

        // Only push when the stage/number moved; bare heartbeats refresh liveness without spamming.
        if (stageAdvanced) {
            TranscriptionStatus transcriptionStatus = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).map(LectureTranscription::getTranscriptionStatus).orElse(null);
            notifyProcessingStateChange(state, transcriptionStatus);
        }
    }

    // -------------------- Checkpoint Processing --------------------

    /** Parse transcription checkpoint data from JSON, expected format {@code {"language": "en", "segments": [...]}}. */
    private TranscriptionCheckpoint parseTranscriptionCheckpoint(String resultJson) {
        var tree = objectMapper.readTree(resultJson);

        var segmentsNode = tree.get("segments");
        if (segmentsNode == null || !segmentsNode.isArray() || segmentsNode.isEmpty()) {
            log.debug("Checkpoint has no segments, ignoring");
            return null;
        }

        String language = tree.has("language") ? tree.get("language").asString("en") : "en";
        List<LectureTranscriptionSegment> segments = objectMapper.convertValue(segmentsNode, new TypeReference<>() {
        });

        boolean isEnriched = segments.stream().anyMatch(seg -> seg.slideNumber() != 0);
        return new TranscriptionCheckpoint(language, segments, isEnriched);
    }

    /**
     * Save transcription data and optionally transition from TRANSCRIBING to INGESTING.
     */
    private void saveTranscription(long lectureUnitId, LectureUnitProcessingState state, TranscriptionCheckpoint checkpoint) {
        LectureUnit unit = state.getLectureUnit();

        // An existing row is updated conditionally on its own id below, not saved blindly.
        Optional<LectureTranscription> existing = transcriptionRepository.findByLectureUnit_Id(lectureUnitId);
        LectureTranscription transcription = existing.orElseGet(() -> {
            var newTranscription = new LectureTranscription(checkpoint.language(), checkpoint.segments(), unit);
            newTranscription.setTranscriptionStatus(TranscriptionStatus.PENDING);
            return newTranscription;
        });

        transcription.setLanguage(checkpoint.language());
        transcription.setSegments(checkpoint.segments());

        if (checkpoint.isEnriched()) {
            // TRANSCRIBING → INGESTING, proven before the write below: a checkpoint that lost ownership
            // must never persist content a requeue may have already deleted the transcription for.
            String jobToken = state.getIngestionJobToken();
            ZonedDateTime now = ZonedDateTime.now();
            if (processingStateRepository.transitionToIngestingIfTranscribing(state.getId(), jobToken, now) == 0) {
                log.debug("Ignoring enriched checkpoint for unit {}: the run is no longer TRANSCRIBING under this token", lectureUnitId);
                return;
            }

            transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
            log.info("Enriched transcription saved for unit {}, transitioning to INGESTING", lectureUnitId);
            persistTranscription(lectureUnitId, jobToken, existing, transcription);

            // Notify UI via WebSocket, mirroring the just-persisted transition without a second read.
            state.resetRetryCount();
            state.transitionTo(ProcessingPhase.INGESTING);
            notifyProcessingStateChange(state, TranscriptionStatus.COMPLETED);
        }
        else {
            // Same reasoning as the enriched branch above.
            String jobToken = state.getIngestionJobToken();
            if (processingStateRepository.touchLastUpdated(state.getId(), jobToken, ZonedDateTime.now()) == 0) {
                log.debug("Ignoring raw checkpoint for unit {}: the run is no longer in flight under this token", lectureUnitId);
                return;
            }

            transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
            log.info("Raw transcription checkpoint saved for unit {}, staying in TRANSCRIBING", lectureUnitId);
            persistTranscription(lectureUnitId, jobToken, existing, transcription);
        }
    }

    /**
     * Conditional update keyed on the row's own id when it existed. A first checkpoint has no id to
     * guard an update on, so it inserts through {@link LectureTranscriptionRepository#insertIfTokenMatches},
     * which folds the ownership check into the insert itself instead of a separate read before it.
     */
    private void persistTranscription(long lectureUnitId, String expectedToken, Optional<LectureTranscription> existing, LectureTranscription transcription) {
        if (existing.isEmpty()) {
            String segmentsJson = new LectureTranscriptionSegmentConverter().convertToDatabaseColumn(transcription.getSegments());
            if (transcriptionRepository.insertIfTokenMatches(lectureUnitId, transcription.getLanguage(), segmentsJson, transcription.getTranscriptionStatus().name(),
                    expectedToken) == 0) {
                log.debug("Skipping transcription insert for unit {}: ownership token changed since it was proven", lectureUnitId);
            }
            return;
        }
        if (transcriptionRepository.updateContentIfExists(transcription.getId(), transcription.getLanguage(), transcription.getSegments(),
                transcription.getTranscriptionStatus()) == 0) {
            log.debug("Skipping transcription write for unit {}: the stored transcription was deleted since this checkpoint's earlier read", lectureUnitId);
        }
    }

    // -------------------- Failure Handling --------------------

    /**
     * Handle processing failure with retry logic. Transitions to FAILED immediately so the UI reflects the
     * error; if retries remain, schedules a backoff, and the dispatcher picks it up and transitions back to
     * TRANSCRIBING/INGESTING when re-dispatched.
     *
     * @param state the processing state that failed
     */
    void handleProcessingFailure(LectureUnitProcessingState state) {
        handleProcessingFailure(state, null);
    }

    /** @see #handleProcessingFailureIfStillLive(LectureUnitProcessingState, String) */
    boolean handleProcessingFailureIfStillLive(LectureUnitProcessingState state) {
        return handleProcessingFailureIfStillLive(state, null);
    }

    /** @see #handleProcessingFailureIfStillLive(LectureUnitProcessingState, String, ZonedDateTime, ZonedDateTime) */
    boolean handleProcessingFailureIfStillLive(LectureUnitProcessingState state, @Nullable String errorCode) {
        return handleProcessingFailureIfStillLive(state, errorCode, null, null);
    }

    /**
     * Handle processing failure with retry logic, forwarding an optional machine-readable error code.
     * Transitions to FAILED immediately so the UI reflects the error; the raw Pyris {@code errorCode} is
     * translated to a specific, instructor-readable i18n key at this state-write boundary (the sole place
     * where classification happens). Permanent input failures (e.g. private/live videos) skip
     * {@code scheduleRetry} entirely, leaving {@code retryEligibleAt == null}, which the dispatcher treats
     * as terminal; transient failures follow the existing exponential backoff until {@code MAX_PROCESSING_RETRIES}.
     *
     * @param state     the processing state that failed
     * @param errorCode machine-readable error code from Pyris (e.g. {@code YOUTUBE_PRIVATE}); may be {@code null}
     */
    void handleProcessingFailure(LectureUnitProcessingState state, @Nullable String errorCode) {
        // Preserve transcription status in the notification so the UI doesn't lose it on failure.
        TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(state.getLectureUnit().getId()).map(LectureTranscription::getTranscriptionStatus).orElse(null);

        LectureIngestionFailureClassifier.FailureComputation computation = LectureIngestionFailureClassifier.computeFailure(state, errorCode);
        processingStateRepository.save(state);
        notifyProcessingStateChange(state, txStatus);

        if (computation.backoffMinutes() != null) {
            log.info("Unit {} failed, scheduled for retry in {} minutes (attempt {}/{})", state.getLectureUnit().getId(), computation.backoffMinutes(), computation.retryCount(),
                    MAX_PROCESSING_RETRIES);
        }
    }

    /**
     * Fail a stalled/stuck run atomically, matching id/phase/token observed at read time, plus (optionally) the liveness signal that justified failing it: a heartbeat can
     * advance lastProgressAt/lastUpdated without touching phase or token, so pinning one of them (see {@link LectureUnitProcessingStateRepository#failIfStillLive}) stops a
     * heartbeat landing between the caller's re-fetch and this write from still failing a run that just became live again. Pass {@code null} for whichever the caller has no
     * observed value for; both are {@code null} for an ordinary dispatch-failure call, which has no staleness decision to protect. Callers never pin both at once, so each pin
     * gets its own repository method with an always-non-null parameter.
     *
     * @param state                  the state read just before this call decided to fail it
     * @param errorCode              machine-readable error code from Pyris; may be {@code null}
     * @param expectedLastProgressAt the stall detector's observed value, or {@code null} not to pin it
     * @param expectedLastUpdated    the stuck detector's observed value, or {@code null} not to pin it
     * @return true when the failure was applied, false when the run had already moved on since the read
     */
    boolean handleProcessingFailureIfStillLive(LectureUnitProcessingState state, @Nullable String errorCode, @Nullable ZonedDateTime expectedLastProgressAt,
            @Nullable ZonedDateTime expectedLastUpdated) {
        ProcessingPhase phaseAtRead = state.getPhase();
        String tokenAtRead = state.getIngestionJobToken();

        LectureIngestionFailureClassifier.FailureComputation computation = LectureIngestionFailureClassifier.computeFailure(state, errorCode);

        int updated;
        if (expectedLastProgressAt != null) {
            updated = processingStateRepository.failIfStillLiveWithProgressPin(state.getId(), phaseAtRead, tokenAtRead, expectedLastProgressAt, computation.retryCount(),
                    computation.errorKey(), computation.retryEligibleAt(), computation.now());
        }
        else if (expectedLastUpdated != null) {
            updated = processingStateRepository.failIfStillLiveWithUpdatedPin(state.getId(), phaseAtRead, tokenAtRead, expectedLastUpdated, computation.retryCount(),
                    computation.errorKey(), computation.retryEligibleAt(), computation.now());
        }
        else {
            updated = processingStateRepository.failIfStillLive(state.getId(), phaseAtRead, tokenAtRead, computation.retryCount(), computation.errorKey(),
                    computation.retryEligibleAt(), computation.now());
        }
        if (updated == 0) {
            log.debug("Unit {} already moved on since it was read as stalled/stuck (phase {}), dropping the stale failure", state.getLectureUnit().getId(), phaseAtRead);
            return false;
        }

        TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(state.getLectureUnit().getId()).map(LectureTranscription::getTranscriptionStatus).orElse(null);
        notifyProcessingStateChange(state, txStatus);
        if (computation.backoffMinutes() != null) {
            log.info("Unit {} failed, scheduled for retry in {} minutes (attempt {}/{})", state.getLectureUnit().getId(), computation.backoffMinutes(), computation.retryCount(),
                    MAX_PROCESSING_RETRIES);
        }
        return true;
    }

    // -------------------- Utility --------------------

    /**
     * Broadcasts a processing state change to all subscribers of the lecture's processing state topic.
     *
     * @param state               the updated processing state
     * @param transcriptionStatus the current transcription status (may be null)
     */
    private void notifyProcessingStateChange(LectureUnitProcessingState state, TranscriptionStatus transcriptionStatus) {
        LectureUnit unit = state.getLectureUnit();
        if (unit == null || unit.getLecture() == null) {
            return;
        }
        long lectureId = unit.getLecture().getId();
        var dto = LectureUnitCombinedStatusDTO.of(unit.getId(), state, transcriptionStatus);
        String topic = PROCESSING_STATE_TOPIC.formatted(lectureId);
        websocketMessagingService.sendMessage(topic, dto);
        log.debug("Sent processing state WebSocket update for unit {} on topic {}", unit.getId(), topic);
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

    /**
     * Delete the stored transcription for a lecture unit so stale text is not re-ingested.
     * <p>
     * Called when the unit's video source changes. Without this, {@link #dispatchPendingJobs()}
     * would find the old {@code COMPLETED} transcription and dispatch the job as {@code INGESTING},
     * ingesting text from the previous video into the vector database.
     *
     * @param unitId the ID of the lecture unit whose transcription should be removed
     */
    void deleteTranscriptionForUnit(long unitId) {
        transcriptionRepository.findByLectureUnit_Id(unitId).ifPresent(transcription -> {
            log.info("Deleting existing transcription for unit {} (video content changed)", unitId);
            transcriptionRepository.delete(transcription);
        });
    }

    // -------------------- Display Page Number Mapping --------------------

    /**
     * Saves the display page numbers received from PyRIS to the attachment (mapping slide numbers to the
     * displayed page numbers detected in the PDF). A {@code null} payload means "no update", so retries or
     * legacy callbacks cannot erase an already persisted mapping.
     */
    private void saveDisplayPageNumbers(LectureUnitProcessingState state, @Nullable List<Integer> displayPageNumbers) {
        if (displayPageNumbers == null) {
            return;
        }
        if (!(state.getLectureUnit() instanceof AttachmentVideoUnit attachmentVideoUnit)) {
            return;
        }
        Attachment attachment = attachmentVideoUnit.getAttachment();
        if (attachment == null) {
            return;
        }
        // Conditional on the recorded version when there is one to guard with; a run whose processing
        // state never recorded a version (no PDF was ever detected for it) has nothing to compare against.
        Integer expectedVersion = state.getAttachmentVersion();
        if (expectedVersion == null) {
            attachmentRepository.updateDisplayPageNumbers(attachment.getId(), displayPageNumbers);
        }
        else if (attachmentRepository.updateDisplayPageNumbersIfVersionMatches(attachment.getId(), displayPageNumbers, expectedVersion) == 0) {
            log.info("Skipping display page number write for unit {}: attachment version changed since this run started", state.getLectureUnit().getId());
        }
    }

    /** Internal DTO for parsed transcription checkpoint data. */
    private record TranscriptionCheckpoint(String language, List<LectureTranscriptionSegment> segments, boolean isEnriched) {
    }
}
