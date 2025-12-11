package de.tum.cit.aet.artemis.lecture.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.service.LectureContentProcessingService;

/**
 * API for lecture content processing operations.
 * This class allows other modules (e.g., nebula, iris) to interact with the lecture
 * content processing pipeline without creating direct dependencies.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class LectureContentProcessingApi extends AbstractLectureApi {

    private final LectureContentProcessingService lectureContentProcessingService;

    public LectureContentProcessingApi(LectureContentProcessingService lectureContentProcessingService) {
        this.lectureContentProcessingService = lectureContentProcessingService;
    }

    /**
     * Triggers processing for an attachment video unit.
     * This will check for TUM Live playlist availability, trigger transcription if needed,
     * and then trigger ingestion.
     *
     * @param unit the attachment video unit to process
     */
    public void triggerProcessing(AttachmentVideoUnit unit) {
        lectureContentProcessingService.triggerProcessing(unit);
    }

    /**
     * Called when a transcription completes (from the polling scheduler).
     * This advances the processing to the ingestion phase.
     *
     * @param transcription the completed transcription
     */
    public void handleTranscriptionComplete(LectureTranscription transcription) {
        lectureContentProcessingService.handleTranscriptionComplete(transcription);
    }

    /**
     * Called when ingestion completes (from the Pyris webhook callback).
     * This marks the processing as DONE or handles failure.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param success       whether ingestion succeeded
     */
    public void handleIngestionComplete(Long lectureUnitId, boolean success) {
        lectureContentProcessingService.handleIngestionComplete(lectureUnitId, success);
    }
}
