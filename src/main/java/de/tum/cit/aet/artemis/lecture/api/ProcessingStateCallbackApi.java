package de.tum.cit.aet.artemis.lecture.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
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
