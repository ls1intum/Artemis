package de.tum.cit.aet.artemis.lecture.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisOrNebulaEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService;

/**
 * API for processing state callbacks.
 * This class allows other modules (e.g., nebula, iris) to notify the lecture
 * content processing pipeline about transcription and ingestion completion
 * without creating circular dependencies.
 * <p>
 * This API was extracted from {@link LectureContentProcessingApi} to break the circular dependency:
 * LectureContentProcessingApi → LectureContentProcessingService → LectureTranscriptionApi →
 * LectureTranscriptionService → LectureContentProcessingApi
 */
@Conditional(LectureWithIrisOrNebulaEnabled.class)
@Controller
@Lazy
public class ProcessingStateCallbackApi extends AbstractLectureApi {

    private final ProcessingStateCallbackService processingStateCallbackService;

    public ProcessingStateCallbackApi(ProcessingStateCallbackService processingStateCallbackService) {
        this.processingStateCallbackService = processingStateCallbackService;
    }

    /**
     * Called when a transcription completes (from the polling scheduler).
     * This advances the processing to the ingestion phase.
     *
     * @param transcription the completed transcription
     */
    public void handleTranscriptionComplete(LectureTranscription transcription) {
        processingStateCallbackService.handleTranscriptionComplete(transcription);
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
