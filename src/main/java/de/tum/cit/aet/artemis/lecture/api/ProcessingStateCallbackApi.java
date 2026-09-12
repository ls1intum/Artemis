package de.tum.cit.aet.artemis.lecture.api;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.dto.ClaimedIngestionUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;
import de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService;

/**
 * API for processing state callbacks from Iris.
 * Allows the iris module to notify the lecture content processing pipeline
 * about checkpoint data and job completion without circular dependencies.
 */
@Conditional(LectureWithIrisEnabled.class)
@Controller
@Lazy
public class ProcessingStateCallbackApi extends AbstractLectureApi {

    private final ProcessingStateCallbackService processingStateCallbackService;

    public ProcessingStateCallbackApi(ProcessingStateCallbackService processingStateCallbackService) {
        this.processingStateCallbackService = processingStateCallbackService;
    }

    /**
     * Handle checkpoint data from an Iris callback (e.g., transcription results).
     * Called on every non-terminal callback that carries a {@code result} payload.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token for validation
     * @param resultJson    the JSON result payload
     */
    public void handleCheckpointData(long lectureUnitId, String jobToken, String resultJson) {
        processingStateCallbackService.handleCheckpointData(lectureUnitId, jobToken, resultJson);
    }

    /**
     * Handle a heartbeat from a running Iris pipeline.
     * Updates {@code lastUpdated} on the processing state so stuck detection
     * can use "time since last callback" instead of "time since phase started",
     * and records the optionally reported stage and progress in the stage ledger.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token for validation
     * @param stageName     name of the stage the run is currently in; may be null (older Iris versions)
     * @param stageProgress progress counter within the stage; may be null
     * @param stageTotal    total work items of the stage; may be null
     */
    public void handleHeartbeat(long lectureUnitId, String jobToken, String stageName, Integer stageProgress, Integer stageTotal) {
        processingStateCallbackService.handleHeartbeat(lectureUnitId, jobToken, stageName, stageProgress, stageTotal);
    }

    /**
     * Claim up to {@code maxJobs} pending IDLE jobs for a pulling Pyris worker.
     *
     * @param workerBootId boot id of the claiming worker process
     * @param maxJobs      how many jobs the worker can take right now
     * @return the claimed units as scalar descriptions for the iris module to prepare and activate
     */
    public List<ClaimedIngestionUnitDTO> claimUnitsForWorker(String workerBootId, int maxJobs) {
        return processingStateCallbackService.claimUnitsForWorker(workerBootId, maxJobs);
    }

    /**
     * Activate a worker claim: transition into the target phase, record job token and fingerprint,
     * and open the worker lease.
     *
     * @param lectureUnitId      the claimed unit
     * @param jobToken           the registered Pyris job token
     * @param targetPhase        the in-flight phase determined at claim time
     * @param contentFingerprint the fingerprint computed at claim time
     * @param workerBootId       boot id of the worker executing the run
     */
    public void activateClaimedJob(long lectureUnitId, String jobToken, ProcessingPhase targetPhase, String contentFingerprint, String workerBootId) {
        processingStateCallbackService.activateClaimedJob(lectureUnitId, jobToken, targetPhase, contentFingerprint, workerBootId);
    }

    /**
     * Mark a claimed unit SKIPPED because preparation found it not processable.
     *
     * @param lectureUnitId the claimed unit
     */
    public void markClaimedUnitSkipped(long lectureUnitId) {
        processingStateCallbackService.markClaimedUnitSkipped(lectureUnitId);
    }

    /**
     * Renew the worker lease of every listed run.
     *
     * @param workerBootId    boot id of the heartbeating worker process
     * @param activeJobTokens the job tokens of every run the worker is currently executing
     * @return the subset of tokens that no longer belong to an in-flight run
     */
    public List<String> renewWorkerLeases(String workerBootId, List<String> activeJobTokens) {
        return processingStateCallbackService.renewWorkerLeases(workerBootId, activeJobTokens);
    }

    /**
     * Called when the processing pipeline completes (terminal Iris callback).
     * Validates the job token and marks the unit as DONE or handles failure.
     *
     * @param lectureUnitId      the ID of the lecture unit
     * @param jobToken           the job token from the callback (for validation)
     * @param success            whether processing succeeded
     * @param errorCode          machine-readable error code (e.g. {@code YOUTUBE_PRIVATE}); {@code null} on success or unknown failure
     * @param displayPageNumbers list of displayed page numbers indexed by slide number (0-based: index 0 = slide 1);
     *                               {@code null} if not applicable or unavailable
     */
    public void handleIngestionComplete(Long lectureUnitId, String jobToken, boolean success, @Nullable String errorCode, @Nullable List<Integer> displayPageNumbers) {
        processingStateCallbackService.handleIngestionComplete(lectureUnitId, jobToken, success, errorCode, displayPageNumbers);
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
        return processingStateCallbackService.findIngestionJobIdentityByToken(token);
    }
}
