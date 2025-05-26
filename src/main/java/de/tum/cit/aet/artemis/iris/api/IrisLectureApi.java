package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;

@Profile(PROFILE_IRIS)
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
     * Adds the specified lecture transcriptions to the vector database in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#addTranscriptionsToPyrisDB(LectureTranscription, Course, Lecture, AttachmentVideoUnit)}.
     * If transcription ingestion is enabled, returns a job token; otherwise returns null.
     *
     * @param transcription       the transcription object containing lecture text
     * @param course              the course to which the lecture belongs
     * @param lecture             the lecture object
     * @param attachmentVideoUnit the attachment video unit associated with the transcriptions
     * @return a job token if ingestion is triggered, otherwise null
     */
    public String addTranscriptionsToPyrisDB(LectureTranscription transcription, Course course, Lecture lecture, AttachmentVideoUnit attachmentVideoUnit) {
        return pyrisWebhookService.addTranscriptionsToPyrisDB(transcription, course, lecture, attachmentVideoUnit);
    }

    /**
     * Deletes the specified lecture transcription from the Pyris database.
     * <p>
     * This method calls {@link PyrisWebhookService#deleteLectureTranscription(LectureTranscription)}.
     *
     * @param lectureTranscription the lecture transcription to delete
     * @return a job token if the deletion is triggered, otherwise null
     */
    public String deleteLectureTranscription(LectureTranscription lectureTranscription) {
        return pyrisWebhookService.deleteLectureTranscription(lectureTranscription);
    }
}
