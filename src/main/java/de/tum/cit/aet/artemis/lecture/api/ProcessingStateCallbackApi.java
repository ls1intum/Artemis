package de.tum.cit.aet.artemis.lecture.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisOrNebulaEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService;

/**
 * API for processing state callbacks from Iris.
 * Allows the iris module to notify the lecture content processing pipeline
 * about checkpoint data and job completion without circular dependencies.
 */
@Conditional(LectureWithIrisOrNebulaEnabled.class)
@Controller
@Lazy
public class ProcessingStateCallbackApi extends AbstractLectureApi {

    private final ProcessingStateCallbackService processingStateCallbackService;

    private static final Logger log = LoggerFactory.getLogger(ProcessingStateCallbackApi.class);

    public ProcessingStateCallbackApi(ProcessingStateCallbackService processingStateCallbackService) {
        this.processingStateCallbackService = processingStateCallbackService;
    }

    /**
     * Legacy callback from Nebula transcription service.
     * No-op in the unified Iris flow — kept temporarily for backwards compatibility during rollout.
     *
     * @param transcription the completed transcription (ignored)
     * @deprecated Will be removed when Nebula module is deprecated. Use Iris checkpoint callbacks instead.
     */
    @Deprecated(forRemoval = true)
    public void handleTranscriptionComplete(LectureTranscription transcription) {
        log.debug("Ignoring legacy Nebula transcription callback for unit {} — unified Iris flow active",
                transcription.getLectureUnit() != null ? transcription.getLectureUnit().getId() : "unknown");
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
     * can use "time since last callback" instead of "time since phase started".
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token for validation
     */
    public void handleHeartbeat(long lectureUnitId, String jobToken) {
        processingStateCallbackService.handleHeartbeat(lectureUnitId, jobToken);
    }

    /**
     * Handle an Iris restart notification.
     * All in-flight jobs are lost — mark them as failed with retry.
     *
     * @return the number of jobs that were reset
     */
    public int handleIrisReset() {
        return processingStateCallbackService.handleIrisReset();
    }

    /**
     * Called when the processing pipeline completes (terminal Iris callback).
     * Validates the job token and marks the unit as DONE or handles failure.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token from the callback (for validation)
     * @param success       whether processing succeeded
     */
    public void handleIngestionComplete(Long lectureUnitId, String jobToken, boolean success) {
        processingStateCallbackService.handleIngestionComplete(lectureUnitId, jobToken, success);
    }
}
