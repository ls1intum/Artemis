package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PROCESSING_RETRIES;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitCombinedStatusDTO;
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
     */
    static final int MAX_CONCURRENT_PROCESSING = 2;

    /**
     * Lock to serialize dispatch so the count check + dispatch are atomic.
     * Without this, concurrent calls to dispatchPendingJobs() can each see the
     * same activeCount and over-dispatch beyond MAX_CONCURRENT_PROCESSING.
     */
    private final ReentrantLock dispatchLock = new ReentrantLock();

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    private static final String PROCESSING_STATE_TOPIC = "/topic/lectures/%d/unit-processing-state";

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final WebsocketMessagingService websocketMessagingService;

    public ProcessingStateCallbackService(LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository,
            Optional<IrisLectureApi> irisLectureApi, WebsocketMessagingService websocketMessagingService) {
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.irisLectureApi = irisLectureApi;
        this.websocketMessagingService = websocketMessagingService;
    }

    // -------------------- Capacity-Aware Dispatch --------------------

    /**
     * Dispatch pending IDLE jobs to Iris, respecting capacity limits.
     * <p>
     * Uses PostgreSQL {@code FOR UPDATE SKIP LOCKED} to safely claim jobs in clustered deployments.
     * Called from three places:
     * <ol>
     * <li>{@link LectureContentProcessingService#triggerProcessing} — immediately after creating IDLE state</li>
     * <li>{@link #handleIngestionComplete} — when a job finishes, filling the freed slot</li>
     * <li>{@link LectureContentProcessingScheduler#processScheduledRetries} — periodic backup every 5 minutes</li>
     * </ol>
     */
    @Transactional
    public void dispatchPendingJobs() {
        if (irisLectureApi.isEmpty()) {
            log.debug("Iris API not available, skipping dispatch");
            return;
        }

        // Serialize dispatch so count + claim + dispatch are atomic.
        // Without this lock, concurrent @Async calls can each see the same
        // activeCount and dispatch beyond MAX_CONCURRENT_PROCESSING.
        dispatchLock.lock();
        try {
            long activeCount = processingStateRepository.countByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING));
            int availableSlots = (int) (MAX_CONCURRENT_PROCESSING - activeCount);

            if (availableSlots <= 0) {
                log.debug("No available slots for dispatch ({} active, max {})", activeCount, MAX_CONCURRENT_PROCESSING);
                return;
            }

            ZonedDateTime now = ZonedDateTime.now();

            // Pick up FAILED jobs that are eligible for retry (backoff expired)
            List<LectureUnitProcessingState> retryJobs = processingStateRepository.findStatesReadyForRetry(ProcessingPhase.FAILED.name(), now);

            for (LectureUnitProcessingState state : retryJobs) {
                if (availableSlots <= 0) {
                    break;
                }
                log.info("Re-dispatching retry-eligible unit {} (attempt {}/{})", state.getLectureUnit().getId(), state.getRetryCount(), MAX_PROCESSING_RETRIES);
                state.clearRetryEligibility();
                dispatchSingleJob(state);
                availableSlots--;
            }

            // Then pick up new IDLE jobs
            List<LectureUnitProcessingState> idleJobs = processingStateRepository.findIdleForDispatch(now, availableSlots);

            if (idleJobs.isEmpty() && retryJobs.isEmpty()) {
                log.debug("No jobs ready for dispatch");
                return;
            }

            if (!idleJobs.isEmpty()) {
                log.info("Dispatching {} IDLE jobs to Iris ({} slots available)", idleJobs.size(), availableSlots);
            }

            for (LectureUnitProcessingState state : idleJobs) {
                dispatchSingleJob(state);
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
        LectureUnit unit = state.getLectureUnit();
        if (!(unit instanceof AttachmentVideoUnit attachmentUnit)) {
            log.warn("Cannot dispatch non-AttachmentVideoUnit (id={})", unit != null ? unit.getId() : "null");
            state.markFailed("artemisApp.attachmentVideoUnit.processing.error.invalidUnitType");
            processingStateRepository.save(state);
            return;
        }

        boolean hasVideo = attachmentUnit.getVideoSource() != null && !attachmentUnit.getVideoSource().isBlank();

        // Check if transcription already completed (e.g., retry after ingestion failure)
        Optional<LectureTranscription> existingTranscription = transcriptionRepository.findByLectureUnit_Id(unit.getId());
        boolean hasCompletedTranscription = existingTranscription.isPresent() && existingTranscription.get().getTranscriptionStatus() == TranscriptionStatus.COMPLETED;

        // Determine target phase
        ProcessingPhase targetPhase;
        if (hasVideo && !hasCompletedTranscription) {
            targetPhase = ProcessingPhase.TRANSCRIBING;
        }
        else {
            targetPhase = ProcessingPhase.INGESTING;
        }

        try {
            String jobToken = irisLectureApi.get().addLectureUnitToPyrisDB(attachmentUnit);

            if (jobToken == null) {
                log.debug("Processing not applicable for unit {} (course settings or content type)", unit.getId());
                state.transitionTo(ProcessingPhase.DONE);
                processingStateRepository.save(state);
                return;
            }

            state.transitionTo(targetPhase);
            state.setIngestionJobToken(jobToken);
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

    // -------------------- Callback Handlers --------------------

    /**
     * Called when the entire processing pipeline completes (from the Iris webhook callback).
     * Validates the job token to reject stale callbacks from old jobs.
     * After completion, dispatches the next pending job to fill the freed slot.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token from the callback
     * @param success       whether processing succeeded
     */
    public void handleIngestionComplete(Long lectureUnitId, String jobToken, boolean success) {
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
            log.info("Processing completed successfully for unit {}", lectureUnitId);
            state.transitionTo(ProcessingPhase.DONE);
            state.setIngestionJobToken(null);
        }
        else {
            log.warn("Processing failed for unit {}", lectureUnitId);
            handleProcessingFailure(state);
        }
        processingStateRepository.save(state);

        // Notify UI via WebSocket
        TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(lectureUnitId).map(LectureTranscription::getTranscriptionStatus).orElse(null);
        notifyProcessingStateChange(state, txStatus);

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
        catch (JsonProcessingException e) {
            log.warn("Failed to parse checkpoint data for unit {}: {}", lectureUnitId, e.getMessage());
        }
    }

    /**
     * Handle a heartbeat from a running Iris pipeline.
     * Updates {@code lastUpdated} so stuck detection can use "time since last callback"
     * instead of "time since phase started".
     * <p>
     * Called on every non-terminal callback that does NOT carry checkpoint data.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token for validation
     */
    public void handleHeartbeat(long lectureUnitId, String jobToken) {
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
        processingStateRepository.save(state);
    }

    // -------------------- Checkpoint Processing --------------------

    /**
     * Parse transcription checkpoint data from JSON.
     * Expected format: {@code {"language": "en", "segments": [...]}}
     */
    private TranscriptionCheckpoint parseTranscriptionCheckpoint(String resultJson) throws JsonProcessingException {
        var tree = objectMapper.readTree(resultJson);

        var segmentsNode = tree.get("segments");
        if (segmentsNode == null || !segmentsNode.isArray() || segmentsNode.isEmpty()) {
            log.debug("Checkpoint has no segments, ignoring");
            return null;
        }

        String language = tree.has("language") ? tree.get("language").asText("en") : "en";
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
        state.incrementRetryCount();
        state.setIngestionJobToken(null);

        if (state.getRetryCount() >= MAX_PROCESSING_RETRIES) {
            log.warn("Max retries reached for unit {}, marking as permanently failed", state.getLectureUnit().getId());
            state.markFailed("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
            processingStateRepository.save(state);
            notifyProcessingStateChange(state, null);
            return;
        }

        // Transition to FAILED so the UI shows the error, but schedule a retry
        long backoffMinutes = calculateBackoffMinutes(state.getRetryCount());
        state.markFailed("artemisApp.attachmentVideoUnit.processing.error.processingFailed");
        state.scheduleRetry(backoffMinutes);
        processingStateRepository.save(state);
        notifyProcessingStateChange(state, null);

        log.info("Unit {} failed, scheduled for retry in {} minutes (attempt {}/{})", state.getLectureUnit().getId(), backoffMinutes, state.getRetryCount(),
                MAX_PROCESSING_RETRIES);
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

    /**
     * Reset a stuck processing state directly to IDLE without touching the retry budget.
     * <p>
     * Used by stuck-job recovery in {@link de.tum.cit.aet.artemis.lecture.service.LectureContentProcessingScheduler}.
     * A job that missed a heartbeat due to a transient network gap is not a content-processing
     * failure — it should be re-queued immediately, not penalised with backoff.
     *
     * @param state the stuck processing state to reset
     */
    void resetToIdleForRecovery(LectureUnitProcessingState state) {
        log.info("Recovering stuck unit {} (was {}) — resetting to IDLE, retry budget preserved", state.getLectureUnit().getId(), state.getPhase());
        state.setPhase(ProcessingPhase.IDLE);
        state.setIngestionJobToken(null);
        state.setStartedAt(null);
        state.setRetryEligibleAt(null);
        state.setLastUpdated(ZonedDateTime.now());
        processingStateRepository.save(state);

        TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(state.getLectureUnit().getId()).map(LectureTranscription::getTranscriptionStatus).orElse(null);
        notifyProcessingStateChange(state, txStatus);
    }

    // -------------------- Iris Reset --------------------

    /**
     * Handle an Iris restart notification.
     * <p>
     * When Iris starts up, all previous in-flight jobs are lost. This method
     * marks all TRANSCRIBING/INGESTING states as failed with retry so they
     * get re-dispatched to the now-fresh Iris instance.
     *
     * @return the number of jobs that were reset
     */
    @Transactional
    public int handleIrisReset() {
        List<LectureUnitProcessingState> activeStates = processingStateRepository.findByPhaseIn(List.of(ProcessingPhase.TRANSCRIBING, ProcessingPhase.INGESTING));

        if (activeStates.isEmpty()) {
            log.info("Iris reset: no active processing jobs to recover");
            return 0;
        }

        log.warn("Iris reset: recovering {} in-flight jobs", activeStates.size());

        for (LectureUnitProcessingState state : activeStates) {
            log.info("Iris reset: re-queuing unit {} (was {}), retry budget preserved", state.getLectureUnit().getId(), state.getPhase());
            // Do NOT call handleProcessingFailure here — Iris restarts are infrastructure events,
            // not content-processing failures. Incrementing retryCount would burn the retry
            // budget and could permanently fail otherwise healthy jobs after a few rollouts.
            state.setPhase(ProcessingPhase.IDLE);
            state.setIngestionJobToken(null);
            state.setStartedAt(null);
            state.setRetryEligibleAt(null);
            state.setLastUpdated(ZonedDateTime.now());
            processingStateRepository.save(state);

            // Notify UI
            TranscriptionStatus txStatus = transcriptionRepository.findByLectureUnit_Id(state.getLectureUnit().getId()).map(LectureTranscription::getTranscriptionStatus)
                    .orElse(null);
            notifyProcessingStateChange(state, txStatus);
        }

        return activeStates.size();
    }

    /**
     * Internal DTO for parsed transcription checkpoint data.
     */
    private record TranscriptionCheckpoint(String language, List<LectureTranscriptionSegment> segments, boolean isEnriched) {
    }
}
