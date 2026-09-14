package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.IngestionCensusDTO;
import de.tum.cit.aet.artemis.lecture.dto.IngestionJobIdentityDTO;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisLectureApi extends AbstractIrisApi {

    private final PyrisWebhookService pyrisWebhookService;

    private final PyrisConnectorService pyrisConnectorService;

    public IrisLectureApi(PyrisWebhookService pyrisWebhookService, PyrisConnectorService pyrisConnectorService) {
        this.pyrisWebhookService = pyrisWebhookService;
        this.pyrisConnectorService = pyrisConnectorService;
    }

    /**
     * Adds the provided PDF attachment video unit to the vector database in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#addLectureUnitToPyrisDB(AttachmentVideoUnit, String, boolean)}.
     * The lecture ingestion must be enabled for the course.
     *
     * @param attachmentVideoUnit the attachment video unit to be added
     * @param contentFingerprint  fingerprint of the unit's source content; stamped verbatim into the vector store by Pyris
     * @param forceReingest       true for quality re-ingestions: Iris bypasses its structural skip checks
     * @return a job token if ingestion is triggered successfully, otherwise null
     */
    public String addLectureUnitToPyrisDB(AttachmentVideoUnit attachmentVideoUnit, String contentFingerprint, boolean forceReingest) {
        return pyrisWebhookService.addLectureUnitToPyrisDB(attachmentVideoUnit, contentFingerprint, forceReingest);
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
     * Deletes lecture units from the vector database in Pyris by their identity alone.
     * Used by the ingestion reconciler to clean up orphaned rows whose lecture unit no longer exists.
     *
     * @param identities course, lecture, and unit ids of the orphaned rows
     */
    public void deleteLectureUnitsByIdentity(List<IngestionJobIdentityDTO> identities) {
        pyrisWebhookService.deleteLectureUnitsByIdentity(identities);
    }

    /**
     * Whether an ingestion request for this unit would actually be dispatched to Pyris
     * (Iris enabled for the course and the unit's content eligible).
     *
     * @param attachmentVideoUnit the unit to check
     * @return true if dispatching the unit would trigger ingestion
     */
    public boolean isLectureUnitProcessable(AttachmentVideoUnit attachmentVideoUnit) {
        return pyrisWebhookService.isLectureUnitProcessableForPyris(attachmentVideoUnit);
    }

    /**
     * Fetch the per-course ingestion census: the aggregated vector index state of every lecture unit
     * of the course, including the stamped content fingerprints.
     *
     * @param courseId the id of the course
     * @return the census, or {@code null} when Pyris does not offer or cannot answer the endpoint
     */
    @Nullable
    public IngestionCensusDTO getIngestionCensus(long courseId) {
        return pyrisConnectorService.getIngestionCensus(courseId);
    }
}
