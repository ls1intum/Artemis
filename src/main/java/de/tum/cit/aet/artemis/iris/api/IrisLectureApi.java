package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;

@Profile(PROFILE_IRIS)
@Controller
public class IrisLectureApi extends AbstractIrisApi {

    private final PyrisWebhookService pyrisWebhookService;

    public IrisLectureApi(PyrisWebhookService pyrisWebhookService) {
        this.pyrisWebhookService = pyrisWebhookService;
    }

    public String addLectureUnitToPyrisDB(AttachmentUnit attachmentUnit) {
        return pyrisWebhookService.addLectureUnitToPyrisDB(attachmentUnit);
    }

    public void deleteLectureFromPyrisDB(List<AttachmentUnit> attachmentUnits) {
        pyrisWebhookService.deleteLectureFromPyrisDB(attachmentUnits);
    }

    public void autoUpdateAttachmentUnitsInPyris(Long courseId, List<AttachmentUnit> newAttachmentUnits) {
        pyrisWebhookService.autoUpdateAttachmentUnitsInPyris(courseId, newAttachmentUnits);
    }
}
