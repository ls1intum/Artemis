package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisLectureApi extends AbstractIrisApi {

    private final PyrisWebhookService pyrisWebhookService;

    public IrisLectureApi(PyrisWebhookService pyrisWebhookService) {
        this.pyrisWebhookService = pyrisWebhookService;
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
     * Updates the lightweight metadata for the provided PDF attachment video unit in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#updateLectureUnitMetadataInPyris(AttachmentVideoUnit)}.
     * The lecture ingestion must be enabled for the course.
     *
     * @param attachmentVideoUnit the attachment video unit whose metadata should be updated
     * @return a dispatch token if the update is triggered successfully, otherwise null
     */
    public String updateLectureUnitMetadataInPyris(AttachmentVideoUnit attachmentVideoUnit) {
        return pyrisWebhookService.updateLectureUnitMetadataInPyris(attachmentVideoUnit);
    }

    /**
     * Updates the lightweight visibility data for the provided PDF attachment video unit in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#updateLectureUnitVisibilityInPyris(AttachmentVideoUnit)}.
     * The lecture ingestion must be enabled for the course.
     *
     * @param attachmentVideoUnit the attachment video unit whose visibility should be updated
     * @return a dispatch token if the update is triggered successfully, otherwise null
     */
    public String updateLectureUnitVisibilityInPyris(AttachmentVideoUnit attachmentVideoUnit) {
        return pyrisWebhookService.updateLectureUnitVisibilityInPyris(attachmentVideoUnit);
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
}
