package de.tum.cit.aet.artemis.lecture.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitProcessingStatusDTO;
import de.tum.cit.aet.artemis.lecture.service.LectureContentProcessingService;

/**
 * API for lecture content processing operations.
 * This class allows other modules (e.g., iris) to interact with the lecture
 * content processing pipeline without creating direct dependencies.
 * <p>
 * Note: Callback methods for transcription and ingestion completion have been moved to
 * {@link ProcessingStateCallbackApi} to break the circular dependency between lecture and iris modules.
 */
@Conditional(LectureWithIrisEnabled.class)
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
     * The authoritative processing status of every lecture unit currently transcribing or ingesting, so the admin
     * ingestion dashboard can source its in-flight view from the same state the unit page reads.
     *
     * @return the in-flight processing statuses
     */
    public List<LectureUnitProcessingStatusDTO> findInFlightProcessingStatuses() {
        return lectureContentProcessingService.findInFlightProcessingStatuses();
    }

    /**
     * Handles cleanup when a lecture unit is being deleted.
     * This cancels any ongoing processing and removes the content from Pyris.
     *
     * @param unit the attachment video unit being deleted
     */
    public void handleUnitDeletion(AttachmentVideoUnit unit) {
        lectureContentProcessingService.handleUnitsDeletion(List.of(unit));
    }

    /**
     * Handles cleanup when multiple lecture units are being deleted (e.g., entire lecture deletion).
     * This cancels any ongoing processing and removes all content from Pyris in a single batch call.
     *
     * @param units the attachment video units being deleted
     */
    public void handleUnitsDeletion(List<AttachmentVideoUnit> units) {
        lectureContentProcessingService.handleUnitsDeletion(units);
    }
}
