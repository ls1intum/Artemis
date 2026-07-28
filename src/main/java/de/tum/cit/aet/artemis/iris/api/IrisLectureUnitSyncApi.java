package de.tum.cit.aet.artemis.iris.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisLectureUnitSyncService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisLectureUnitSyncApi extends AbstractIrisApi {

    private final PyrisLectureUnitSyncService pyrisLectureUnitSyncService;

    public IrisLectureUnitSyncApi(PyrisLectureUnitSyncService pyrisLectureUnitSyncService) {
        this.pyrisLectureUnitSyncService = pyrisLectureUnitSyncService;
    }

    /**
     * Updates lightweight lecture unit metadata in Pyris without sending PDF or transcription payloads.
     *
     * @param attachmentVideoUnit the attachment video unit whose metadata changed
     * @return a dispatch token if the update was sent, otherwise null
     */
    public String updateLectureUnitMetadataInPyris(AttachmentVideoUnit attachmentVideoUnit) {
        return pyrisLectureUnitSyncService.updateLectureUnitMetadataInPyris(attachmentVideoUnit);
    }

    /**
     * Updates lightweight lecture unit visibility in Pyris without sending PDF or transcription payloads.
     *
     * @param attachmentVideoUnit the attachment video unit whose visibility changed
     * @param slides              all slides belonging to the lecture unit
     * @return a dispatch token if the update was sent, otherwise null
     */
    public String updateLectureUnitVisibilityInPyris(AttachmentVideoUnit attachmentVideoUnit, List<Slide> slides) {
        return pyrisLectureUnitSyncService.updateLectureUnitVisibilityInPyris(attachmentVideoUnit, slides);
    }
}
