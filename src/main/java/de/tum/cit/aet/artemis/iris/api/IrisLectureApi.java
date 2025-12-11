package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

@Profile(PROFILE_IRIS)
@Controller
@Lazy
public class IrisLectureApi extends AbstractIrisApi {

    private final PyrisWebhookService pyrisWebhookService;

    private final PyrisJobService pyrisJobService;

    public IrisLectureApi(PyrisWebhookService pyrisWebhookService, PyrisJobService pyrisJobService) {
        this.pyrisWebhookService = pyrisWebhookService;
        this.pyrisJobService = pyrisJobService;
    }

    /**
     * Adds the provided PDF attachment video unit to the vector database in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#addLectureUnitToPyrisDB(AttachmentVideoUnit)}.
     * The lecture ingestion must be enabled for the course.
     *
     * @param attachmentVideoUnit the attachment video unit to be added
     * @return a job token if ingestion is triggered successfully, otherwise null
     */
    public String addLectureUnitToPyrisDB(AttachmentVideoUnit attachmentVideoUnit) {
        return pyrisWebhookService.addLectureUnitToPyrisDB(attachmentVideoUnit);
    }

    /**
     * Deletes the given lecture's attachments from the vector database in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#deleteLectureFromPyrisDB(List)}.
     *
     * @param attachmentVideoUnits the list of attachment video units to be removed
     */
    public void deleteLectureFromPyrisDB(List<AttachmentVideoUnit> attachmentVideoUnits) {
        pyrisWebhookService.deleteLectureFromPyrisDB(attachmentVideoUnits);
    }

    /**
     * Updates or creates the specified attachment video units in the Pyris database automatically if auto-ingestion is enabled.
     * <p>
     * This method calls {@link PyrisWebhookService#autoUpdateAttachmentVideoUnitsInPyris(List)}.
     *
     * @param newAttachmentVideoUnits the new attachment video units to be sent to Pyris
     */
    public void autoUpdateAttachmentVideoUnitsInPyris(List<AttachmentVideoUnit> newAttachmentVideoUnits) {
        pyrisWebhookService.autoUpdateAttachmentVideoUnitsInPyris(newAttachmentVideoUnits);
    }

    /**
     * Cancel any pending ingestion jobs for a lecture unit.
     * Used when a unit is deleted or content changes during ingestion.
     * <p>
     * Note: This only removes the local job tracking. If Pyris has already started
     * processing, it will complete but the webhook callback will be ignored.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @return true if a job was cancelled, false otherwise
     */
    public boolean cancelPendingIngestion(long lectureUnitId) {
        return pyrisJobService.removeLectureIngestionJobByUnitId(lectureUnitId);
    }
}
