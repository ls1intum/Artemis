package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.IrisLectureUnitSyncState;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.IrisLectureUnitSyncStateRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;
import de.tum.cit.aet.artemis.videosource.service.VideoSourceResolverService;

/**
 * Service that hands out queued jobs to pulling Pyris workers and handles the callbacks and state transitions of the lecture content processing pipeline.
 * <p>
 * The {@code lecture_unit_processing_state} table acts as a database-backed job queue. A Pyris worker claims jobs through conditional atomic UPDATEs (see
 * {@link LectureUnitProcessingStateRepository#claimIdleForDispatch}) rather than row locks, so claiming is safe in clustered Artemis deployments. Artemis never pushes a
 * job to Pyris: a queued job waits until a worker claims it.
 * <p>
 * This service handles:
 * <ul>
 * <li>Worker claims: claiming retry-eligible and IDLE jobs and activating them under a worker lease</li>
 * <li>Ingestion completion callbacks from Iris webhooks</li>
 * <li>Failure handling with retry logic</li>
 * </ul>
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class ProcessingStateCallbackService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingStateCallbackService.class);

    /**
     * How long a retry claim keeps a row out of the candidate list. It has to outlast the dispatch the claim belongs
     * to, and it doubles as the recovery window: a node killed mid-dispatch leaves the claim in place until it lapses,
     * after which the row is eligible again. Intended to match the scheduler's no-callback timeout
     * ({@code artemis.iris.ingestion.no-callback-timeout-minutes}), which is the point at which a dispatch is no
     * longer considered in flight — keep the two in sync if either is overridden.
     */
    private final int retryClaimLeaseMinutes;

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    private final AttachmentRepository attachmentRepository;

    private final ProcessingStateNotificationService notificationService;

    private final LectureUnitContentFingerprintService contentFingerprintService;

    private final FeatureToggleService featureToggleService;

    private final VideoSourceResolverService videoSourceResolver;

    /** Upper bound on jobs handed out per single worker claim call, purely as a sanity clamp. */
    private final int maxJobsPerClaim;

    /**
     * How many dispatch claims a unit may take without reaching an outcome before the next one is replaced by a charged failure. Runs lost to a
     * restart, a lapsed lease, or an abandoned claim are recovered without charging the retry budget; this limit is what keeps a unit that
     * keeps losing its runs from being re-dispatched forever.
     */
    private final int maxUnsettledAttempts;

    /** Error code for a unit whose claims reached {@link #maxUnsettledAttempts} without an outcome. */
    static final String RECOVERY_LIMIT_REACHED = "RECOVERY_LIMIT_REACHED";

    private final IrisLectureUnitSyncStateRepository irisLectureUnitSyncStateRepository;

    public ProcessingStateCallbackService(LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository,
            AttachmentRepository attachmentRepository, ProcessingStateNotificationService notificationService, LectureUnitContentFingerprintService contentFingerprintService,
            FeatureToggleService featureToggleService, VideoSourceResolverService videoSourceResolver,
            @Value("${artemis.iris.ingestion.retry-claim-lease-minutes:20}") int retryClaimLeaseMinutes,
            @Value("${artemis.iris.ingestion.max-jobs-per-claim:8}") int maxJobsPerClaim, @Value("${artemis.iris.ingestion.max-unsettled-attempts:3}") int maxUnsettledAttempts,
            IrisLectureUnitSyncStateRepository irisLectureUnitSyncStateRepository) {
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.attachmentRepository = attachmentRepository;
        this.notificationService = notificationService;
        this.contentFingerprintService = contentFingerprintService;
        this.featureToggleService = featureToggleService;
        this.videoSourceResolver = videoSourceResolver;
        this.retryClaimLeaseMinutes = retryClaimLeaseMinutes;
        this.maxJobsPerClaim = maxJobsPerClaim;
        this.maxUnsettledAttempts = maxUnsettledAttempts;
        this.irisLectureUnitSyncStateRepository = irisLectureUnitSyncStateRepository;
    }

    /**
     * Returns a synchronization state to the retry pass now that Pyris holds the lecture unit: a row settled as {@link IrisLectureUnitSyncState#STATUS_NOT_INGESTED} or
     * {@link IrisLectureUnitSyncState#STATUS_FAILED} is skipped by the retry query and not recreated by the backfill, so ingestion completing is what reopens it; a row that is
     * {@link IrisLectureUnitSyncState#STATUS_IN_PROGRESS} is reopened too, since its in-flight request can still answer "not ingested" and reopening tells the listener that.
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

    // -------------------- Claim Helpers --------------------

    /**
     * The instant a claim is stamped with. Truncated to whole seconds so the value held in memory
     * matches what {@code started_at} and {@code retry_eligible_at} can store: they are legacy DATETIME columns
     * keeping only whole seconds on MySQL, which rounds anything finer on write.
     *
     * @return the claim's timestamp, used for lease expiry and sweep cutoffs
     */
    static ZonedDateTime claimTimestamp() {
        return ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * A fresh identity for one claim. Every claim gets its own, so a superseded claim can never be mistaken for the
     * one that replaced it however close together they were taken -- which whole-second timestamps cannot guarantee.
     *
     * @return the claim identity to write and later match on
     */
    private static String newClaimToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Replace the claim of a unit that reached {@link #maxUnsettledAttempts} with a charged failure. The write repeats the claim's own eligibility
     * and pins the retry count read here, so it applies at most once and never to a row that moved on.
     *
     * @param state     the candidate from the dispatch batch read
     * @param retryPath whether the candidate came from the retry queue rather than the IDLE queue
     * @return true when the unit is at the limit, so the caller must not claim it
     */
    private boolean failIfAttemptsExhausted(LectureUnitProcessingState state, boolean retryPath) {
        if (state.getUnsettledAttempts() < maxUnsettledAttempts) {
            return false;
        }
        int expectedRetryCount = state.getRetryCount();
        LectureIngestionFailureClassifier.FailureComputation computation = LectureIngestionFailureClassifier.computeFailure(state, RECOVERY_LIMIT_REACHED);
        int updated = retryPath
                ? processingStateRepository.failExhaustedRetryAttempts(state.getId(), expectedRetryCount, computation.retryCount(), computation.errorKey(),
                        computation.retryEligibleAt(), maxUnsettledAttempts, computation.now())
                : processingStateRepository.failExhaustedIdleAttempts(state.getId(), expectedRetryCount, computation.retryCount(), computation.errorKey(),
                        computation.retryEligibleAt(), maxUnsettledAttempts, computation.now());
        if (updated == 1) {
            log.warn("Unit {} lost {} runs in a row without an outcome; charging a failure instead of dispatching it again", state.getLectureUnit().getId(), maxUnsettledAttempts);
            notificationService.notifyWithTranscriptionStatus(state);
        }
        return true;
    }

    // Fail a dispatch that never reached Pyris, bound to the claim that produced it: the caller holds the
    // pre-dispatch snapshot, so committing it wholesale would overwrite a requeue or newer claim landed since.
    private void failDispatchIfStillClaimed(LectureUnitProcessingState state, String claimToken) {
        LectureIngestionFailureClassifier.FailureComputation computation = LectureIngestionFailureClassifier.computeFailure(state, null);
        int updated = processingStateRepository.failDispatchIfStillClaimed(state.getId(), claimToken, computation.retryCount(), computation.errorKey(),
                computation.retryEligibleAt(), computation.now());
        if (updated == 0) {
            log.info("Not failing processing state {}: its dispatch claim is no longer current (requeued, re-claimed, or already activated)", state.getId());
            return;
        }
        notificationService.notifyWithTranscriptionStatus(state);
    }

    // Terminally fail a claimed state during preparation, bound to its claim like failDispatchIfStillClaimed.
    // These are local problems Pyris cannot fix, so no retry is scheduled. Returns false if the claim moved on.
    private boolean failPreparationIfStillClaimed(LectureUnitProcessingState state, String claimToken, String errorKey) {
        if (processingStateRepository.failPreparationIfStillClaimed(state.getId(), claimToken, errorKey, ZonedDateTime.now()) == 0) {
            log.info("Not failing processing state {} during preparation: its dispatch claim is no longer current", state.getId());
            return false;
        }
        // Mirror the committed outcome onto the in-memory entity so any notification below describes the row as it now is.
        state.markFailed(errorKey);
        return true;
    }

    /**
     * The dispatch-ready description of a claimed state, which the worker claim returns to the worker.
     */
    private record PreparedDispatch(LectureUnitProcessingState state, AttachmentVideoUnit unit, ProcessingPhase targetPhase, String contentFingerprint) {
    }

    /**
     * Run the Artemis side of a dispatch on a claimed state: unit type check,
     * target phase determination, and content fingerprinting. States that cannot be dispatched are
     * terminally handled here (FAILED with a specific key) and reported as {@code null}.
     *
     * @param state      a state freshly claimed for dispatch
     * @param claimToken identity of the claim that produced it; every terminal outcome here is committed through an atomic update matching it, so fingerprinting (which
     *                       reads the attachment from disk and can be slow) cannot end up overwriting a requeue that landed while it ran
     * @return the prepared dispatch, or {@code null} when the state was failed here instead
     */
    @Nullable
    private PreparedDispatch prepareClaimedState(LectureUnitProcessingState state, String claimToken) {
        LectureUnit unit = state.getLectureUnit();
        if (!(unit instanceof AttachmentVideoUnit attachmentUnit)) {
            log.warn("Cannot dispatch non-AttachmentVideoUnit (id={})", unit != null ? unit.getId() : "null");
            failPreparationIfStillClaimed(state, claimToken, "artemisApp.attachmentVideoUnit.processing.error.invalidUnitType");
            return null;
        }

        // Only a supported source can be transcribed; Iris ingests any other link as a unit without a video
        boolean hasVideo = videoSourceResolver.isSupportedSource(attachmentUnit.getVideoSource());

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
                if (processingStateRepository.requeueIfStillClaimed(state.getId(), claimToken, ZonedDateTime.now()) == 0) {
                    log.info("Not requeuing unit {}: its dispatch claim is no longer current", unit.getId());
                }
                return null;
            }
            // A genuinely unreadable file or malformed link is a local problem Pyris cannot fix, so retrying would
            // burn the retry budget uselessly — fail with a specific key instead of aborting the whole claimed batch.
            log.error("Cannot read attachment for unit {}, marking as FAILED without dispatch: {}", unit.getId(), e.getMessage());
            if (failPreparationIfStillClaimed(state, claimToken, "artemisApp.attachmentVideoUnit.processing.error.attachmentUnreadable")) {
                notificationService.notifyProcessingStateChange(state, null);
            }
            return null;
        }
        return new PreparedDispatch(state, attachmentUnit, targetPhase, contentFingerprint);
    }

    // -------------------- Pull-Based Worker Dispatch --------------------

    /**
     * Claim up to {@code maxJobs} pending jobs for a pulling Pyris worker: expired retries first, then IDLE work in priority order (fresh uploads before backlog). Each
     * candidate goes through a per-row conditional claim, so no row locks span the loop. A claim whose activation never arrives (worker died between claim and execution)
     * recovers on its own: an IDLE claim is released by the abandoned-claim sweep, a retry claim lapses with its lease. No capacity check happens here — capacity belongs to
     * the worker, which only claims what it can run.
     *
     * @param workerBootId boot id of the claiming Pyris worker process
     * @param maxJobs      how many jobs the worker can take right now
     * @return the claimed units, described by scalars for the iris module to prepare and activate
     */
    public List<ClaimedIngestionUnitDTO> claimUnitsForWorker(String workerBootId, int maxJobs) {
        if (!featureToggleService.isFeatureEnabled(Feature.LectureContentProcessing)) {
            // The toggle is the operator's kill switch for new work; retries, backfill and reconcile all honor it,
            // so a pulling worker must not become a way around it.
            log.debug("LectureContentProcessing feature is disabled, handing out no jobs to worker {}", workerBootId);
            return List.of();
        }
        int jobs = Math.clamp(maxJobs, 0, maxJobsPerClaim);
        if (jobs == 0) {
            return List.of();
        }
        ZonedDateTime now = claimTimestamp();
        List<LectureUnitProcessingState> claimed = new ArrayList<>();

        for (LectureUnitProcessingState state : processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), now, jobs)) {
            if (failIfAttemptsExhausted(state, true)) {
                continue;
            }
            ZonedDateTime leaseExpiry = now.plusMinutes(retryClaimLeaseMinutes);
            String claimToken = newClaimToken();
            if (processingStateRepository.claimRetryEligible(state.getId(), claimToken, now, leaseExpiry, maxUnsettledAttempts) == 0) {
                continue;
            }
            // Mirror the claim onto the loaded entity so a later save cannot write back the stale value.
            state.setRetryEligibleAt(leaseExpiry);
            state.setClaimToken(claimToken);
            log.info("Worker re-claiming retry-eligible unit {} (attempt {}/{})", state.getLectureUnit().getId(), state.getRetryCount(), MAX_PROCESSING_RETRIES);
            claimed.add(state);
        }

        int remaining = jobs - claimed.size();
        if (remaining > 0) {
            for (LectureUnitProcessingState state : processingStateRepository.findIdleForDispatch(now, remaining)) {
                if (failIfAttemptsExhausted(state, false)) {
                    continue;
                }
                String claimToken = newClaimToken();
                if (processingStateRepository.claimIdleForDispatch(state.getId(), claimToken, now, maxUnsettledAttempts) == 0) {
                    continue;
                }
                state.setStartedAt(now);
                state.setClaimToken(claimToken);
                claimed.add(state);
            }
        }

        List<ClaimedIngestionUnitDTO> result = new ArrayList<>();
        for (LectureUnitProcessingState state : claimed) {
            // Exactly one of these is set at claim time: startedAt for an IDLE claim, retryEligibleAt for a
            // retry claim (mirrors activateClaimedJob's own claim-shape check just above). Read before preparing,
            // since preparation commits its own terminal outcomes against this same marker.
            String claimToken = state.getClaimToken();
            PreparedDispatch prepared = prepareClaimedState(state, claimToken);
            if (prepared != null) {
                result.add(new ClaimedIngestionUnitDTO(prepared.unit().getId(), prepared.contentFingerprint(), state.isForceReingest(), prepared.targetPhase(), claimToken));
            }
        }
        if (!result.isEmpty()) {
            log.info("Worker {} claimed {} jobs", workerBootId, result.size());
        }
        return result;
    }

    /**
     * Activate a claim once the iris side registered the job token and handed the payload to the worker: transition into the target phase, record token/fingerprint, and open
     * the worker lease — from here the run is alive exactly as long as the worker keeps renewing it. Bound to the exact claim that produced it by {@code claimToken}: an
     * activation arriving after the claim was released and re-claimed by a newer, still-unactivated claim matches nothing instead of activating that newer claim with this
     * stale job token.
     *
     * @param lectureUnitId      the claimed unit
     * @param jobToken           the registered Pyris job token
     * @param targetPhase        the in-flight phase determined at claim time
     * @param contentFingerprint the fingerprint computed at claim time
     * @param workerBootId       boot id of the worker executing the run
     * @param claimToken         identity of the claim being activated, from {@link ClaimedIngestionUnitDTO#claimToken()}
     * @return whether the claim was activated; false when the unit no longer holds this exact claim
     */
    public boolean activateClaimedJob(long lectureUnitId, String jobToken, ProcessingPhase targetPhase, String contentFingerprint, String workerBootId, String claimToken) {
        int activated = processingStateRepository.activateClaimedJob(lectureUnitId, targetPhase, jobToken, contentFingerprint, workerBootId, claimToken, ZonedDateTime.now());
        if (activated == 0) {
            log.warn("Ignoring activation of unit {} by worker {}: the unit no longer holds the claim (released, re-claimed, or already activated)", lectureUnitId, workerBootId);
            return false;
        }
        log.info("Worker {} activated unit {} as {} with token {}", workerBootId, lectureUnitId, targetPhase, maskToken(jobToken));

        // The activation above is committed, so the run is live: a notification failure must not turn into a failed
        // activation, which would abort the worker's whole claim response and strand this batch until its leases lapse.
        try {
            processingStateRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(notificationService::notifyWithTranscriptionStatus);
        }
        catch (Exception e) {
            log.warn("Activated unit {} but could not push the state change to clients: {}", lectureUnitId, e.getMessage());
        }
        return true;
    }

    /**
     * Mark a claimed unit SKIPPED (not processable), bound like {@link #activateClaimedJob} to the claim that
     * produced it: a stale result from a lapsed, re-claimed claim matches nothing instead of cancelling the newer run.
     *
     * @param lectureUnitId the claimed unit
     * @param claimToken    identity of the claim, from {@link de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO#claimToken()}
     * @return true when marked SKIPPED, false when the claim was no longer current
     */
    public boolean markClaimedUnitSkipped(long lectureUnitId, String claimToken) {
        int updated = processingStateRepository.markSkippedIfStillClaimed(lectureUnitId, claimToken, ZonedDateTime.now());
        if (updated == 0) {
            log.info("Not marking unit {} SKIPPED: its claim is no longer current (released, re-claimed, or already activated)", lectureUnitId);
            return false;
        }
        log.info("Processing not applicable for claimed unit {} (course settings or content type), marking as SKIPPED", lectureUnitId);
        return true;
    }

    /**
     * Charge a failure to a claimed unit whose preparation for the worker failed, bound to that claim: the retry budget bounds a
     * preparation error that repeats, instead of the claim being released and re-claimed for free.
     *
     * @param lectureUnitId the claimed unit
     * @param claimToken    identity of the claim, from {@link de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO#claimToken()}
     */
    public void failClaimedUnitPreparation(long lectureUnitId, String claimToken) {
        processingStateRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(state -> failDispatchIfStillClaimed(state, claimToken));
    }

    /**
     * Renew the worker lease of every listed run and report back the tokens Artemis no longer recognizes as in flight, so the worker can stop executing runs whose lease was
     * reclaimed. Each renewal is also pushed to the client: the badge derives liveness from renewal freshness, so a stopped stream visibly loses contact.
     *
     * @param workerBootId    boot id of the heartbeating worker process
     * @param activeJobTokens the job tokens of every run the worker is currently executing
     * @return the subset of tokens that no longer belong to an in-flight run
     */
    public List<String> renewWorkerLeases(String workerBootId, List<String> activeJobTokens) {
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
            // Push a fresh read, not the row read above: a stage heartbeat can commit while the renewal waits on a row
            // lock (e.g. a checkpoint's transcription insert), and the older snapshot would briefly roll the badge back.
            // The renewal above is committed, so a failed push must not abort the batch: the worker's later leases
            // would go unrenewed and lapse, and its revoked tokens would never be reported back.
            try {
                processingStateRepository.findById(state.getId()).ifPresent(notificationService::notifyWithTranscriptionStatus);
            }
            catch (Exception e) {
                log.warn("Renewed the lease of unit {} but could not push the state change to clients: {}", state.getLectureUnit().getId(), e.getMessage());
            }
        }
        if (!revoked.isEmpty()) {
            log.warn("Worker {} heartbeat listed {} run(s) Artemis no longer tracks, reporting them revoked", workerBootId, revoked.size());
        }
        return revoked;
    }

    // -------------------- Callback Handlers --------------------

    /**
     * Called when the entire processing pipeline completes (from the Iris webhook callback). Validates the job token to reject stale callbacks from old jobs. The worker
     * claims its next job on its own once the run frees its capacity.
     *
     * @param lectureUnitId      the ID of the lecture unit
     * @param jobToken           the job token from the callback
     * @param success            whether processing succeeded
     * @param errorCode          machine-readable error code (e.g. {@code YOUTUBE_PRIVATE}); {@code null} on success or unknown failure
     * @param displayPageNumbers displayed page numbers indexed by slide number (0-based); {@code null} if unavailable
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
            // Reopens the Iris synchronization before the terminal claim below, not after: nothing here shares a transaction, so completeIngestionIfLive commits
            // independently the moment it runs. Doing that first and reopening synchronization second would mean a synchronization-update failure lands after the run is
            // already durably DONE with its token cleared -- non-replayable, since a retried callback would then see a token mismatch and be dropped, leaving the
            // synchronization row un-reopened for good. Running this first instead means such a failure leaves nothing committed for this callback: the run is still
            // live under the same token, so a retried delivery reaches this exact code path again. Deliberately unguarded for the same reason -- swallowing a failure
            // here would strand the unit just as silently.
            irisLectureUnitSyncStateRepository.updateWithLectureUnitLock(lectureUnitId, ProcessingStateCallbackService::reopenSynchronization);

            // Atomic claim + terminal write in one statement: see completeIngestionIfLive.
            if (processingStateRepository.completeIngestionIfLive(state.getId(), jobToken, ZonedDateTime.now()) == 0) {
                log.info("Ignoring completion callback for unit {}: the run is no longer in flight under this token", lectureUnitId);
                return;
            }
            log.info("Processing completed successfully for unit {}", lectureUnitId);

            // Written only once ownership is confirmed; see saveDisplayPageNumbers for the version guard.
            saveDisplayPageNumbers(state, displayPageNumbers);
            // Mirror the just-persisted transition for an accurate notification, without a second read.
            state.transitionTo(ProcessingPhase.DONE);
            state.setIngestionJobToken(null);
            state.setConfirmedFingerprint(state.getContentFingerprint());
            state.setForceReingest(null);

            notificationService.notifyWithTranscriptionStatus(state);
        }
        else {
            log.warn("Processing failed for unit {} (errorCode={})", lectureUnitId, errorCode);
            // Same atomic-claim reasoning as the success branch, via failIfStillLive.
            if (!handleProcessingFailureIfStillLive(state, errorCode, null, null)) {
                log.info("Ignoring completion callback for unit {}: the run is no longer in flight under this token", lectureUnitId);
                return;
            }
        }
    }

    /**
     * Handle a heartbeat from a running Iris pipeline: updates {@code lastUpdated} (so stuck detection can use "time since last callback" rather than "time since phase started")
     * and records optional stage/progress (so stalled runs become detectable). Called on every non-terminal callback that does NOT carry checkpoint data.
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
        // Merged under the row lock against the committed progress, so an overlapping older heartbeat cannot roll it back.
        // Only a moved stage or counter is pushed; bare heartbeats refresh liveness without spamming.
        processingStateRepository.applyHeartbeatLocked(stateOpt.get().getId(), jobToken, ZonedDateTime.now(), stageName, stageProgress, stageTotal).ifPresent(advanced -> {
            TranscriptionStatus transcriptionStatus = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).map(LectureTranscription::getTranscriptionStatus).orElse(null);
            notificationService.notifyProcessingStateChange(advanced, transcriptionStatus);
        });
    }

    // -------------------- Failure Handling --------------------

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

        notificationService.notifyWithTranscriptionStatus(state);
        if (computation.backoffMinutes() != null) {
            log.info("Unit {} failed, scheduled for retry in {} minutes (attempt {}/{})", state.getLectureUnit().getId(), computation.backoffMinutes(), computation.retryCount(),
                    MAX_PROCESSING_RETRIES);
        }
        return true;
    }

    // -------------------- Utility --------------------

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
     * Delete the stored transcription for a lecture unit so stale text is not re-ingested. Called when the unit's video source changes: without this, the next claim
     * would find the old {@code COMPLETED} transcription and start the job as {@code INGESTING}, ingesting text from the previous video into the vector database.
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
}
