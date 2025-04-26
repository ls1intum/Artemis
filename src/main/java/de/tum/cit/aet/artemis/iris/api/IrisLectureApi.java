package de.tum.cit.aet.artemis.iris.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.VideoUnit;

@Profile(PROFILE_IRIS)
@Controller
public class IrisLectureApi extends AbstractIrisApi {

    private final PyrisWebhookService pyrisWebhookService;

    public IrisLectureApi(PyrisWebhookService pyrisWebhookService) {
        this.pyrisWebhookService = pyrisWebhookService;
    }

    /**
     * Adds the provided PDF attachment unit to the vector database in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#addLectureUnitToPyrisDB(AttachmentUnit)}.
     * The lecture ingestion must be enabled for the course.
     *
     * @param attachmentUnit the attachment unit to be added
     * @return a job token if ingestion is triggered successfully, otherwise null
     */
    public String addLectureUnitToPyrisDB(AttachmentUnit attachmentUnit) {
        return pyrisWebhookService.addLectureUnitToPyrisDB(attachmentUnit);
    }

    /**
     * Deletes the given lecture's attachments from the vector database in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#deleteLectureFromPyrisDB(List)}.
     *
     * @param attachmentUnits the list of attachment units to be removed
     */
    public void deleteLectureFromPyrisDB(List<AttachmentUnit> attachmentUnits) {
        pyrisWebhookService.deleteLectureFromPyrisDB(attachmentUnits);
    }

    /**
     * Updates or creates the specified attachment units in the Pyris database automatically if auto-ingestion is enabled.
     * <p>
     * This method calls {@link PyrisWebhookService#autoUpdateAttachmentUnitsInPyris(Long, List)}.
     *
     * @param courseId           the ID of the course
     * @param newAttachmentUnits the new attachment units to be sent to Pyris
     */
    public void autoUpdateAttachmentUnitsInPyris(Long courseId, List<AttachmentUnit> newAttachmentUnits) {
        pyrisWebhookService.autoUpdateAttachmentUnitsInPyris(courseId, newAttachmentUnits);
    }

    /**
     * Adds the specified lecture transcriptions to the vector database in Pyris.
     * <p>
     * This method calls {@link PyrisWebhookService#addTranscriptionsToPyrisDB(LectureTranscription, Course, Lecture, VideoUnit)}.
     * If transcription ingestion is enabled, returns a job token; otherwise returns null.
     *
     * @param transcription the transcription object containing lecture text
     * @param course        the course to which the lecture belongs
     * @param lecture       the lecture object
     * @param lectureUnit   the video unit associated with the transcriptions
     * @return a job token if ingestion is triggered, otherwise null
     */
    public String addTranscriptionsToPyrisDB(LectureTranscription transcription, Course course, Lecture lecture, VideoUnit lectureUnit) {
        return pyrisWebhookService.addTranscriptionsToPyrisDB(transcription, course, lecture, lectureUnit);
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
