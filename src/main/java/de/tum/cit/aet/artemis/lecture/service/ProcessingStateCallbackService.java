package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Service that handles callbacks, capacity-aware dispatch, and state transitions for the lecture content processing pipeline.
 * <p>
 * The {@code lecture_unit_processing_state} table acts as a database-backed job queue using PostgreSQL's
 * {@code FOR UPDATE SKIP LOCKED} pattern for safe concurrent dispatch in clustered Artemis deployments.
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
     * after which the row is eligible again. Matches the scheduler's no-callback timeout, which is the point at which
     * a dispatch is no longer considered in flight.
     */
    private static final int RETRY_CLAIM_LEASE_MINUTES = 20;

    /**
     * Lock to serialize dispatch so the count check + dispatch are atomic.
     * Without this, concurrent calls to dispatchPendingJobs() can each see the
     * same activeCount and over-dispatch beyond MAX_CONCURRENT_PROCESSING.
     */
    private final ReentrantLock dispatchLock = new ReentrantLock();

    private static final JsonMapper objectMapper = JsonObjectMapper.get();

    private static final String PROCESSING_STATE_TOPIC = "/topic/lectures/%d/unit-processing-state";

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    private final AttachmentRepository attachmentRepository;

    // ObjectProvider rather than Optional: this service sits on the cycle
    // irisLectureApi -> pyrisWebhookService -> pyrisJobService -> processingStateCallbackApi -> this,
    // and Optional (like @Lazy on a final type) is resolved eagerly at construction.
    // The provider defers resolution to dispatch time, when all beans exist.
    private final ObjectProvider<IrisLectureApi> irisLectureApi;

    private final WebsocketMessagingService websocketMessagingService;

    private final LectureUnitContentFingerprintService contentFingerprintService;

    private final DistributedDataProvider distributedDataProvider;

    /**
     * How long after the last claim or heartbeat call a pulling Pyris worker still counts as present.
     * While a worker is present, the legacy push dispatch is suppressed and IDLE jobs simply wait in
     * the queue for the next claim; when the worker disappears past this grace (an old Iris without
     * worker support, or the worker gone for good), push dispatch resumes automatically. Sized as a
     * generous multiple of the worker's heartbeat interval so one lost heartbeat never flips modes.
     */
    private static final Duration WORKER_MODE_GRACE = Duration.ofSeconds(90);

    /** Upper bound on jobs handed out per single worker claim call, purely as a sanity clamp. */
    private static final int MAX_JOBS_PER_CLAIM = 8;

    private static final String WORKER_MAP_NAME = "pyris-ingestion-worker";

    private static final String WORKER_LAST_SEEN_KEY = "lastSeenAt";

    @Nullable
    private DistributedMap<String, String> workerMap;

    public ProcessingStateCallbackService(LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository,
            AttachmentRepository attachmentRepository, ObjectProvider<IrisLectureApi> irisLectureApi, WebsocketMessagingService websocketMessagingService,
            LectureUnitContentFingerprintService contentFingerprintService, DistributedDataProvider distributedDataProvider,
            @Value("${artemis.iris.ingestion.max-concurrent-jobs:2}") int maxConcurrentJobs) {
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.attachmentRepository = attachmentRepository;
        this.irisLectureApi = irisLectureApi;
        this.websocketMessagingService = websocketMessagingService;
        this.contentFingerprintService = contentFingerprintService;
        this.distributedDataProvider = distributedDataProvider;
        this.maxConcurrentJobs = maxConcurrentJobs;
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

    // -------------------- Capacity-Aware Dispatch --------------------

    /**
     * Dispatch pending IDLE jobs to Iris, respecting capacity limits.
     * <p>
     * Claim-commit-then-send: the repository atomically claims rows (marking {@code startedAt}) in its
     * own committed transaction BEFORE any HTTP request leaves this node. A crash between claim and send
     * therefore never spawns a duplicate pipeline; it merely leaves a claimed row that the scheduler's
     * claim-expiry release requeues. This also means no database row locks are held across the calls to
     * Pyris. Claim order implements the queue priority: fresh work first, retries second, backlog last.
     * <p>
     * Called from three places:
     * <ol>
     * <li>{@link LectureContentProcessingService#triggerProcessing} — immediately after creating IDLE state</li>
     * <li>{@link #handleIngestionComplete} — when a job finishes, filling the freed slot</li>
     * <li>{@link LectureContentProcessingScheduler#processScheduledRetries} — periodic backup every 5 minutes</li>
     * </ol>
     * <p>
     * Cluster safety comes from the conditional claim on each candidate rather than from a transaction spanning the
     * read and the write: see {@link LectureUnitProcessingStateRepository#claimJobsForDispatch}. The local
     * {@code dispatchLock} still serializes dispatch within this node so the capacity check cannot be raced by two of
     * its own threads.
     */
    public void dispatchPendingJobs() {
        if (irisLectureApi.getIfAvailable() == null) {
            log.debug("Iris API not available, skipping dispatch");
            return;
        }

        // Pull mode: while a Pyris worker is actively claiming and heartbeating, jobs are not pushed —
        // they wait as IDLE rows for the worker's next claim, which owns its capacity itself. The push
        // below survives only as the automatic fallback for an Iris without worker support.
        if (isWorkerModeActive()) {
            log.debug("Pyris worker active, leaving pending jobs for pull-based claim");
            return;
        }

        // Serialize dispatch so count + claim + dispatch are atomic per node.
        // Without this lock, concurrent @Async calls can each see the same
        // activeCount and dispatch beyond the configured maximum.
        dispatchLock.lock();
        try {
            long activeCount = processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING));
            int availableSlots = (int) (maxConcurrentJobs - activeCount);

            if (availableSlots <= 0) {
                log.debug("No available slots for dispatch ({} active, max {})", activeCount, maxConcurrentJobs);
                return;
            }

            List<LectureUnitProcessingState> claimedJobs = processingStateRepository.claimJobsForDispatch(ZonedDateTime.now(), availableSlots);
            if (claimedJobs.isEmpty()) {
                log.debug("No jobs ready for dispatch");
                return;
            }
            log.info("Dispatching {} claimed jobs to Iris ({} slots available)", claimedJobs.size(), availableSlots);

            for (LectureUnitProcessingState state : claimedJobs) {
                if (state.getRetryCount() > 0) {
                    log.info("Re-dispatching retry-eligible unit {} (attempt {}/{})", state.getLectureUnit().getId(), state.getRetryCount(), MAX_PROCESSING_RETRIES);
                }
                // Isolate each dispatch: an unexpected failure on one claimed unit must not abort the
                // remaining claims (which would otherwise stay claimed until the expiry sweep and starve
                // the queue). Mirrors the per-unit guard in the backfill loop.
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

    /**
     * Dispatch a single IDLE job to Iris.
     * Determines whether to start as TRANSCRIBING or INGESTING based on existing transcription data.
     */
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
            String jobToken = irisLectureApi.getObject().addLectureUnitToPyrisDB(attachmentUnit, contentFingerprint, state.isForceReingest());

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

            // Notify UI via WebSocket
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
     * @param state a state freshly claimed by {@code claimJobsForDispatch}
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
                // The file store path is not initialized yet: a claim raced application startup before
                // FilePathConverter was configured, so resolving the attachment path threw. The attachment is
                // NOT unreadable — the store simply is not ready — so this is transient. Requeue for immediate
                // re-dispatch once startup completes, rather than permanently failing a healthy unit with
                // attachmentUnreadable (a permanent key the reconciler never revives). Startup normally sets the
                // path before serving; this is the belt-and-braces guard for any request that still races it.
                log.warn("File store not initialized yet; requeuing unit {} for re-dispatch instead of failing it", unit.getId());
                state.requeue();
                processingStateRepository.save(state);
                return null;
            }
            // The attachment file genuinely cannot be read or its link is malformed (an unreadable file
            // surfaces as IllegalStateException; a malformed link surfaces as IllegalArgumentException from
            // URI.create). This is a local problem Pyris cannot fix, so retrying against Iris would burn the
            // whole retry budget without ever dispatching. Fail with a specific permanent key rather than
            // letting the exception escape and abort the rest of the claimed batch; re-uploading resets it.
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
     * Claim up to {@code maxJobs} pending IDLE jobs for a pulling Pyris worker. The same
     * {@code FOR UPDATE SKIP LOCKED} claim as the push path stamps {@code startedAt}, so a claim
     * whose activation never arrives (worker died between claim and execution) is released by the
     * existing claim-expiry sweep. No capacity check happens here: in pull mode capacity belongs to
     * the worker, which only claims what it can run.
     *
     * @param workerBootId boot id of the claiming Pyris worker process
     * @param maxJobs      how many jobs the worker can take right now
     * @return the claimed units, described by scalars for the iris module to prepare and activate
     */
    public List<ClaimedIngestionUnitDTO> claimUnitsForWorker(String workerBootId, int maxJobs) {
        markWorkerSeen(workerBootId);
        int jobs = Math.clamp(maxJobs, 0, MAX_JOBS_PER_CLAIM);
        if (jobs == 0) {
            return List.of();
        }
        List<LectureUnitProcessingState> claimed = processingStateRepository.claimJobsForDispatch(ZonedDateTime.now(), jobs);
        List<ClaimedIngestionUnitDTO> result = new ArrayList<>();
        for (LectureUnitProcessingState state : claimed) {
            if (state.getRetryCount() > 0) {
                log.info("Worker re-claiming retry-eligible unit {} (attempt {}/{})", state.getLectureUnit().getId(), state.getRetryCount(), MAX_PROCESSING_RETRIES);
            }
            PreparedDispatch prepared = prepareClaimedState(state);
            if (prepared != null) {
                result.add(new ClaimedIngestionUnitDTO(prepared.unit().getId(), prepared.contentFingerprint(), state.isForceReingest(), prepared.targetPhase()));
            }
        }
        if (!result.isEmpty()) {
            log.info("Worker {} claimed {} jobs", workerBootId, result.size());
        }
        return result;
    }

    /**
     * Activate a claim after the iris side registered the job token and handed the payload to the
     * worker: transition into the target phase, record token and fingerprint, and open the worker
     * lease. From here on the run is alive exactly as long as the worker keeps renewing the lease.
     *
     * @param lectureUnitId      the claimed unit
     * @param jobToken           the registered Pyris job token
     * @param targetPhase        the in-flight phase determined at claim time
     * @param contentFingerprint the fingerprint computed at claim time
     * @param workerBootId       boot id of the worker executing the run
     */
    public void activateClaimedJob(long lectureUnitId, String jobToken, ProcessingPhase targetPhase, String contentFingerprint, String workerBootId) {
        Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByLectureUnit_Id(lectureUnitId);
        if (stateOpt.isEmpty()) {
            log.warn("Cannot activate claimed job for unit {}: no processing state", lectureUnitId);
            return;
        }
        LectureUnitProcessingState state = stateOpt.get();
        state.transitionTo(targetPhase);
        state.setIngestionJobToken(jobToken);
        state.setContentFingerprint(contentFingerprint);
        state.renewLease(workerBootId);
        processingStateRepository.save(state);
        log.info("Worker {} activated unit {} as {} with token {}", workerBootId, lectureUnitId, targetPhase, maskToken(jobToken));

        TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).map(LectureTranscription::getTranscriptionStatus).orElse(null);
        notifyProcessingStateChange(state, txStatus);
    }

    /**
     * Mark a claimed unit SKIPPED because preparation on the iris side found it not processable
     * (course settings or content type). Mirrors the push path's null-token branch.
     *
     * @param lectureUnitId the claimed unit
     */
    public void markClaimedUnitSkipped(long lectureUnitId) {
        processingStateRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(state -> {
            log.info("Processing not applicable for claimed unit {} (course settings or content type), marking as SKIPPED", lectureUnitId);
            state.transitionTo(ProcessingPhase.SKIPPED);
            processingStateRepository.save(state);
        });
    }

    /**
     * Renew the worker lease of every listed run and report back the tokens Artemis no longer
     * recognizes as in flight, so the worker can stop executing runs whose lease was reclaimed. Each
     * renewal is also pushed to the client: the badge derives liveness from the freshness of these
     * updates, so a run whose stream of renewals stops visibly loses contact within seconds.
     *
     * @param workerBootId    boot id of the heartbeating worker process
     * @param activeJobTokens the job tokens of every run the worker is currently executing
     * @return the subset of tokens that no longer belong to an in-flight run
     */
    public List<String> renewWorkerLeases(String workerBootId, List<String> activeJobTokens) {
        markWorkerSeen(workerBootId);
        List<String> revoked = new ArrayList<>();
        for (String token : activeJobTokens) {
            Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByIngestionJobToken(token);
            if (stateOpt.isEmpty() || !stateOpt.get().isProcessing()) {
                revoked.add(token);
                continue;
            }
            LectureUnitProcessingState state = stateOpt.get();
            state.renewLease(workerBootId);
            processingStateRepository.save(state);
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
     * Whether a pulling Pyris worker has claimed or heartbeated within {@link #WORKER_MODE_GRACE}.
     */
    boolean isWorkerModeActive() {
        String lastSeen = getWorkerMap().get(WORKER_LAST_SEEN_KEY);
        if (lastSeen == null) {
            return false;
        }
        try {
            return Instant.parse(lastSeen).isAfter(Instant.now().minus(WORKER_MODE_GRACE));
        }
        catch (DateTimeParseException e) {
            return false;
        }
    }

    // -------------------- Callback Handlers --------------------

    /**
     * Called when the entire processing pipeline completes (from the Iris webhook callback).
     * Validates the job token to reject stale callbacks from old jobs.
     * After completion, dispatches the next pending job to fill the freed slot.
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

        // Atomically claim the terminal transition: only the first callback carrying the live token
        // clears it. Two concurrent callbacks for the same run (e.g. a success and a failure racing)
        // would otherwise both pass the in-memory token check above and both write a terminal state.
        if (processingStateRepository.clearIngestionJobTokenIfMatches(state.getId(), jobToken) == 0) {
            log.info("Ignoring concurrent duplicate completion callback for unit {} (token already claimed)", lectureUnitId);
            return;
        }

        if (success) {
            log.info("Processing completed successfully for unit {}", lectureUnitId);
            // Save the display page numbers BEFORE the terminal state write: a crash between the two
            // then leaves the run in flight (healed by re-dispatch, which idempotently overwrites the
            // mapping) instead of a DONE state whose page numbers are permanently missing.
            saveDisplayPageNumbers(state, displayPageNumbers);
            state.transitionTo(ProcessingPhase.DONE);
            state.setIngestionJobToken(null);
            state.setConfirmedFingerprint(state.getContentFingerprint());
            state.setForceReingest(null);
            processingStateRepository.save(state);

            // Notify UI via WebSocket
            TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).map(LectureTranscription::getTranscriptionStatus).orElse(null);
            notifyProcessingStateChange(state, txStatus);
        }
        else {
            log.warn("Processing failed for unit {} (errorCode={})", lectureUnitId, errorCode);
            // handleProcessingFailure saves the state and sends WebSocket notification internally
            handleProcessingFailure(state, errorCode);
        }

        // Fill the freed slot with the next pending job
        dispatchPendingJobs();
    }

    /**
     * Handle checkpoint data from Iris callbacks (e.g., transcription results).
     * <p>
     * Iris sends transcription data in the {@code result} field of status callbacks.
     * This method parses the transcript JSON, saves it to the database, and transitions
     * from TRANSCRIBING to INGESTING when the enriched transcript arrives.
     * <p>
     * Checkpoint types (distinguished by segment content):
     * <ul>
     * <li>Raw transcript (all slideNumber=0): saved as PENDING, stay in TRANSCRIBING</li>
     * <li>Enriched transcript (some slideNumber≠0): saved as COMPLETED, transition to INGESTING</li>
     * </ul>
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

        // Validate token
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
     * Resolve the identity of the ingestion job currently associated with the given token.
     * Backs the database fallback for authenticating Iris ingestion callbacks after the
     * distributed job map entry expired.
     *
     * @param token the ingestion job token from the callback
     * @return the job identity if a processing state currently carries this token
     */
    public Optional<IngestionJobIdentityDTO> findIngestionJobIdentityByToken(String token) {
        return processingStateRepository.findIngestionJobIdentityByToken(token);
    }

    /**
     * Handle a heartbeat from a running Iris pipeline.
     * Updates {@code lastUpdated} so stuck detection can use "time since last callback"
     * instead of "time since phase started", and records the optionally reported stage
     * and progress so stalled runs (heartbeats without progress) become detectable.
     * <p>
     * Called on every non-terminal callback that does NOT carry checkpoint data.
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

        state.setLastUpdated(ZonedDateTime.now());
        boolean stageAdvanced = state.recordStageProgress(stageName, stageProgress, stageTotal);
        processingStateRepository.save(state);

        // Push the live stage counter to the client only when the stage or the number actually moved.
        // Bare heartbeats still refresh liveness above, but do not spam the WebSocket every few seconds.
        if (stageAdvanced) {
            TranscriptionStatus transcriptionStatus = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).map(LectureTranscription::getTranscriptionStatus).orElse(null);
            notifyProcessingStateChange(state, transcriptionStatus);
        }
    }

    // -------------------- Checkpoint Processing --------------------

    /**
     * Parse transcription checkpoint data from JSON.
     * Expected format: {@code {"language": "en", "segments": [...]}}
     */
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

        // Find or create transcription entity
        LectureTranscription transcription = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).orElseGet(() -> {
            var newTranscription = new LectureTranscription(checkpoint.language(), checkpoint.segments(), unit);
            newTranscription.setTranscriptionStatus(TranscriptionStatus.PENDING);
            return newTranscription;
        });

        // Update with latest data
        transcription.setLanguage(checkpoint.language());
        transcription.setSegments(checkpoint.segments());

        if (checkpoint.isEnriched()) {
            transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
            log.info("Enriched transcription saved for unit {}, transitioning to INGESTING", lectureUnitId);

            transcriptionRepository.save(transcription);

            // Transition: TRANSCRIBING → INGESTING (keep same job token — Iris continues the pipeline)
            state.resetRetryCount();
            state.transitionTo(ProcessingPhase.INGESTING);
            processingStateRepository.save(state);

            // Notify UI via WebSocket
            notifyProcessingStateChange(state, TranscriptionStatus.COMPLETED);
        }
        else {
            transcription.setTranscriptionStatus(TranscriptionStatus.PENDING);
            log.info("Raw transcription checkpoint saved for unit {}, staying in TRANSCRIBING", lectureUnitId);
            transcriptionRepository.save(transcription);

            // Update lastUpdated as heartbeat (prevents stuck detection)
            state.setLastUpdated(ZonedDateTime.now());
            processingStateRepository.save(state);
        }
    }

    // -------------------- Failure Handling --------------------

    /**
     * Handle processing failure with retry logic.
     * <p>
     * Transitions to FAILED immediately so the UI reflects the error.
     * If retries remain, schedules a backoff — the dispatcher will pick it up
     * and transition back to TRANSCRIBING/INGESTING when re-dispatched.
     *
     * @param state the processing state that failed
     */
    void handleProcessingFailure(LectureUnitProcessingState state) {
        handleProcessingFailure(state, null);
    }

    /**
     * Handle processing failure with retry logic, forwarding an optional machine-readable error code.
     * <p>
     * Transitions to FAILED immediately so the UI reflects the error. The raw Pyris {@code errorCode}
     * is translated to a specific, instructor-readable i18n key at this state-write boundary (the sole
     * place where classification happens).
     * <p>
     * Permanent input failures (e.g. private/live videos) skip {@code scheduleRetry} entirely — the state
     * is left with {@code retryEligibleAt == null}, which the dispatcher treats as terminal for auto-retries.
     * Transient failures follow the existing exponential backoff until {@code MAX_PROCESSING_RETRIES}.
     *
     * @param state     the processing state that failed
     * @param errorCode machine-readable error code from Pyris (e.g. {@code YOUTUBE_PRIVATE}); may be {@code null}
     */
    void handleProcessingFailure(LectureUnitProcessingState state, @Nullable String errorCode) {
        state.incrementRetryCount();
        state.setIngestionJobToken(null);
        // Undo the dispatch attempt, including the claim that started it: startedAt is what marks a job as taken, so
        // leaving it set would keep this unit out of the idle queue for good.
        state.setStartedAt(null);

        // Preserve existing transcription status in the WebSocket notification so the UI
        // does not lose it when a failure occurs after transcription already completed.
        TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(state.getLectureUnit().getId()).map(LectureTranscription::getTranscriptionStatus).orElse(null);

        ProcessingErrorClassification classification = classifyIngestionFailure(errorCode);
        state.markFailed(classification.errorKey());

        boolean maxRetriesReached = state.getRetryCount() >= MAX_PROCESSING_RETRIES;
        if (maxRetriesReached || !classification.retryable()) {
            if (!classification.retryable()) {
                log.warn("Unit {} failed with permanent error code '{}', no retry scheduled", state.getLectureUnit().getId(), errorCode);
            }
            else {
                log.warn("Max retries reached for unit {}, marking as permanently failed", state.getLectureUnit().getId());
            }
            processingStateRepository.save(state);
            notifyProcessingStateChange(state, txStatus);
            return;
        }

        long backoffMinutes = calculateBackoffMinutes(state.getRetryCount());
        state.scheduleRetry(backoffMinutes);
        processingStateRepository.save(state);
        notifyProcessingStateChange(state, txStatus);

        log.info("Unit {} failed, scheduled for retry in {} minutes (attempt {}/{})", state.getLectureUnit().getId(), backoffMinutes, state.getRetryCount(),
                MAX_PROCESSING_RETRIES);
    }

    /**
     * Translate a raw Pyris {@code error_code} into a specific, instructor-readable i18n key plus a
     * retryability flag. Unknown and blank codes fall back to the generic key with retryable = true.
     */
    static ProcessingErrorClassification classifyIngestionFailure(@Nullable String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.processingFailed", true);
        }
        return switch (rawCode) {
            case "YOUTUBE_PRIVATE" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubePrivate", false);
            case "YOUTUBE_LIVE" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubeLive", false);
            case "YOUTUBE_TOO_LONG" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubeTooLong", false);
            case "YOUTUBE_UNAVAILABLE" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubeUnavailable", false);
            case "YOUTUBE_DOWNLOAD_FAILED" -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.youtubeDownloadFailed", true);
            default -> new ProcessingErrorClassification("artemisApp.attachmentVideoUnit.processing.error.processingFailed", true);
        };
    }

    /**
     * Result of classifying a raw Pyris failure code at the state-write boundary.
     *
     * @param errorKey  i18n key stored on the processing state; shown to instructors via the status tooltip
     * @param retryable whether the failure is eligible for automatic retry (permanent input errors are not)
     */
    record ProcessingErrorClassification(String errorKey, boolean retryable) {
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

    // -------------------- Utility --------------------

    /**
     * Send a WebSocket notification about a processing state change.
     * Broadcasts the updated status to all subscribers of the lecture's processing state topic in the UI.
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
     * Saves the display page numbers received from PyRIS to the attachment.
     * The list maps slide numbers to the displayed page numbers detected in the PDF.
     * A {@code null} payload is treated as "no update" so retries or legacy callbacks
     * cannot erase an already persisted mapping.
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
        attachment.setDisplayPageNumbers(displayPageNumbers);
        attachmentRepository.save(attachment);
    }

    /**
     * Internal DTO for parsed transcription checkpoint data.
     */
    private record TranscriptionCheckpoint(String language, List<LectureTranscriptionSegment> segments, boolean isEnriched) {
    }
}
