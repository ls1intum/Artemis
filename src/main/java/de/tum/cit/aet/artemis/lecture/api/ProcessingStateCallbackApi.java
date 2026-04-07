package de.tum.cit.aet.artemis.lecture.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService;

/**
 * API for processing state callbacks from Iris.
 * Allows the iris module to notify the lecture content processing pipeline
 * about ingestion completion without creating circular dependencies.
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
     * Called when ingestion completes (from the Pyris webhook callback).
     * This marks the processing as DONE or handles failure.
     * <p>
     * The jobToken is validated against the stored token in the processing state.
     * Stale callbacks from old jobs (after content change/restart) are ignored.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token from the callback (for validation)
     * @param success       whether ingestion succeeded
     */
    public void handleIngestionComplete(Long lectureUnitId, String jobToken, boolean success) {
        processingStateCallbackService.handleIngestionComplete(lectureUnitId, jobToken, success);
    }
}
