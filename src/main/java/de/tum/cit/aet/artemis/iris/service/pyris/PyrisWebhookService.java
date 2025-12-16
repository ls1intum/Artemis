package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;

@Lazy
@Service
@Profile(PROFILE_IRIS)
public class PyrisWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PyrisWebhookService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final IrisSettingsService irisSettingsService;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final Optional<LectureTranscriptionsRepositoryApi> lectureTranscriptionsRepositoryApi;

    private final InstanceMessageSendService instanceMessageSendService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisWebhookService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi,
            Optional<LectureTranscriptionsRepositoryApi> lectureTranscriptionsRepositoryApi, @Lazy InstanceMessageSendService instanceMessageSendService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.lectureTranscriptionsRepositoryApi = lectureTranscriptionsRepositoryApi;
        this.instanceMessageSendService = instanceMessageSendService;
    }

    private String attachmentToBase64(AttachmentVideoUnit attachmentVideoUnit) {
        Path path = FilePathConverter.fileSystemPathForExternalUri(URI.create(attachmentVideoUnit.getAttachment().getLink()), FilePathType.ATTACHMENT_UNIT);
        try {
            byte[] fileBytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(fileBytes);
        }
        catch (IOException e) {
            throw new IrisInternalPyrisErrorException(e.getMessage());
        }
    }

    private PyrisLectureUnitWebhookDTO processAttachmentVideoUnitForUpdate(AttachmentVideoUnit attachmentVideoUnit) {
        Lecture lecture = attachmentVideoUnit.getLecture();
        Course course = attachmentVideoUnit.getLecture().getCourse();

        Long lectureUnitId = attachmentVideoUnit.getId();
        String lectureUnitName = attachmentVideoUnit.getName();

        Long lectureId = lecture.getId();
        String lectureTitle = lecture.getTitle();
        Long courseId = course.getId();
        String courseTitle = course.getTitle();
        String courseDescription = course.getDescription() == null ? "" : course.getDescription();
        String base64EncodedPdf;
        String lectureUnitLink;
        if (attachmentVideoUnit.getAttachment() != null) {
            base64EncodedPdf = attachmentToBase64(attachmentVideoUnit);
            lectureUnitLink = artemisBaseUrl + "/" + attachmentVideoUnit.getAttachment().getLink();
        }
        else {
            base64EncodedPdf = "";
            lectureUnitLink = "";
        }

        LectureTranscriptionsRepositoryApi transcriptionsRepositoryApi = lectureTranscriptionsRepositoryApi
                .orElseThrow(() -> new LectureApiNotPresentException(LectureTranscriptionsRepositoryApi.class));
        Optional<LectureTranscription> lectureTranscription = transcriptionsRepositoryApi.findByLectureUnit_Id(attachmentVideoUnit.getId());
        int version = attachmentVideoUnit.getAttachment() != null ? attachmentVideoUnit.getAttachment().getVersion() : 1;

        LectureUnitRepositoryApi api = lectureUnitRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureUnitRepositoryApi.class));
        api.save(attachmentVideoUnit);

        if (lectureTranscription.isPresent()) {
            LectureTranscription transcription = lectureTranscription.get();

            return new PyrisLectureUnitWebhookDTO(base64EncodedPdf, attachmentVideoUnit.getAttachment() != null ? attachmentVideoUnit.getAttachment().getVersion() : -1,
                    transcription, lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle, courseDescription, lectureUnitLink,
                    attachmentVideoUnit.getVideoSource());
        }

        return new PyrisLectureUnitWebhookDTO(base64EncodedPdf, attachmentVideoUnit.getAttachment() != null ? attachmentVideoUnit.getAttachment().getVersion() : null, null,
                lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle, courseDescription, lectureUnitLink, attachmentVideoUnit.getVideoSource());
    }

    /**
     * Processes an {@link AttachmentVideoUnit} for deletion by creating a dummy DTO with only the necessary IDs.
     * This method returns a {@link PyrisLectureUnitWebhookDTO} where most fields are empty or have default values,
     * as only the IDs are required by Pyris to identify and delete the lecture unit from its database.
     *
     * @param attachmentVideoUnit The lecture unit to be processed for deletion.
     * @return A {@link PyrisLectureUnitWebhookDTO} with IDs for deletion and empty/default values for other fields.
     */
    private PyrisLectureUnitWebhookDTO processAttachmentVideoUnitForDeletion(AttachmentVideoUnit attachmentVideoUnit) {
        Long lectureUnitId = attachmentVideoUnit.getId();
        Long lectureId = attachmentVideoUnit.getLecture().getId();
        Long courseId = attachmentVideoUnit.getLecture().getCourse().getId();
        return new PyrisLectureUnitWebhookDTO("", 0, null, lectureUnitId, "", lectureId, "", courseId, "", "", "", "");
    }

    /**
     * send the updated / created attachment to Pyris for ingestion if autoLecturesUpdate is enabled
     *
     * @param newAttachmentVideoUnits the new attachment Units to be sent to pyris for ingestion
     */
    public void autoUpdateAttachmentVideoUnitsInPyris(List<AttachmentVideoUnit> newAttachmentVideoUnits) {
        var course = newAttachmentVideoUnits.stream().map(AttachmentVideoUnit::getLecture).filter(Objects::nonNull).map(Lecture::getCourse).filter(Objects::nonNull).findFirst();

        if (course.isEmpty()) {
            return;
        }

        var settings = irisSettingsService.getSettingsForCourse(course.get());
        if (!settings.enabled()) {
            return;
        }
        for (AttachmentVideoUnit attachmentVideoUnit : newAttachmentVideoUnits) {
            instanceMessageSendService.sendLectureUnitAutoIngestionSchedule(attachmentVideoUnit.getId());
        }
    }

    /**
     * delete the lectures from the vector database on pyris
     *
     * @param attachmentVideoUnits The attachmentVideoUnit that got Updated / erased
     * @return jobToken if the job was created
     */
    public String deleteLectureFromPyrisDB(List<AttachmentVideoUnit> attachmentVideoUnits) {
        List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentVideoUnits = new ArrayList<>();
        attachmentVideoUnits.stream().filter(
                unit -> (unit.getAttachment().getAttachmentType() == AttachmentType.FILE && unit.getAttachment().getLink().endsWith(".pdf")) || unit.getVideoSource() != null)
                .forEach(unit -> {
                    toUpdateAttachmentVideoUnits.add(processAttachmentVideoUnitForDeletion(unit));
                });
        if (!toUpdateAttachmentVideoUnits.isEmpty()) {
            return executeLectureDeletionWebhook(toUpdateAttachmentVideoUnits);
        }
        return null;
    }

    /**
     * adds the lectures to the vector database in Pyris
     *
     * @param attachmentVideoUnit The attachmentVideoUnit that got Updated
     * @return jobToken if the job was created else null
     */
    public String addLectureUnitToPyrisDB(AttachmentVideoUnit attachmentVideoUnit) {
        if (irisSettingsService.isEnabledForCourse(attachmentVideoUnit.getLecture().getCourse()) && !attachmentVideoUnit.getLecture().isTutorialLecture()) {
            if ((attachmentVideoUnit.getVideoSource() != null && !attachmentVideoUnit.getVideoSource().isEmpty()) || (attachmentVideoUnit.getAttachment() != null
                    && (attachmentVideoUnit.getAttachment().getAttachmentType() == AttachmentType.FILE && attachmentVideoUnit.getAttachment().getLink().endsWith(".pdf")))) {
                return executeLectureAdditionWebhook(processAttachmentVideoUnitForUpdate(attachmentVideoUnit), attachmentVideoUnit.getLecture().getCourse());
            }
        }
        return null;
    }

    /**
     * executes executeLectureWebhook add or delete lectures from to the vector database on pyris
     *
     * @param toUpdateAttachmentVideoUnits The attachmentVideoUnit that are goin to be deleted
     * @return jobToken if the job was created
     */
    private String executeLectureDeletionWebhook(List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentVideoUnits) {
        String jobToken = pyrisJobService.addLectureIngestionWebhookJob(0, 0, 0);
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, artemisBaseUrl, "default");
        PyrisWebhookLectureDeletionExecutionDTO executionDTO = new PyrisWebhookLectureDeletionExecutionDTO(toUpdateAttachmentVideoUnits, settingsDTO, List.of());
        pyrisConnectorService.executeLectureDeletionWebhook(executionDTO);
        return jobToken;
    }

    /**
     * executes executeLectureAdditionWebhook add lecture from to the vector database on pyris
     *
     * @param toUpdateAttachmentVideoUnit The attachmentVideoUnit that are going to be updated
     * @param course                      The course of the attachment video unit
     * @return jobToken if the job was created
     */
    private String executeLectureAdditionWebhook(PyrisLectureUnitWebhookDTO toUpdateAttachmentVideoUnit, Course course) {
        String jobToken = pyrisJobService.addLectureIngestionWebhookJob(toUpdateAttachmentVideoUnit.courseId(), toUpdateAttachmentVideoUnit.lectureId(),
                toUpdateAttachmentVideoUnit.lectureUnitId());
        var settings = irisSettingsService.getSettingsForCourse(course);
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, artemisBaseUrl, settings.variant().jsonValue());
        PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(toUpdateAttachmentVideoUnit,
                toUpdateAttachmentVideoUnit.lectureUnitId(), settingsDTO, List.of());
        pyrisConnectorService.executeLectureAdditionWebhook(executionDTO);
        return jobToken;
    }

    /**
     * send the updated / created faqs to Pyris for ingestion if autoLecturesUpdate is enabled.
     *
     * @param newFaq the new faqs to be sent to pyris for ingestion
     */
    public void autoUpdateFaqInPyris(Faq newFaq) {
        var course = newFaq.getCourse();
        var settings = irisSettingsService.getSettingsForCourse(course);

        if (settings.enabled()) {
            addFaq(newFaq);
        }
    }

    /**
     * adds the faq to Pyris.
     *
     * @param faq The faq that will be added to pyris
     * @return jobToken if the job was created else null
     */
    public String addFaq(Faq faq) {
        if (irisSettingsService.isEnabledForCourse(faq.getCourse())) {
            return executeFaqAdditionWebhook(new PyrisFaqWebhookDTO(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCourse().getId(),
                    faq.getCourse().getTitle(), faq.getCourse().getDescription()), faq.getCourse());
        }
        return null;
    }

    /**
     * executes the faq addition webhook to add faq to the vector database on pyris
     *
     * @param toUpdateFaq The faq that got Updated as webhook DTO
     * @param course      The course of the faq
     * @return jobToken if the job was created else null
     */

    private String executeFaqAdditionWebhook(PyrisFaqWebhookDTO toUpdateFaq, Course course) {
        String jobToken = pyrisJobService.addFaqIngestionWebhookJob(toUpdateFaq.courseId(), toUpdateFaq.faqId());
        var settings = irisSettingsService.getSettingsForCourse(course);
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, artemisBaseUrl, settings.variant().jsonValue());
        PyrisWebhookFaqIngestionExecutionDTO executionDTO = new PyrisWebhookFaqIngestionExecutionDTO(toUpdateFaq, settingsDTO, List.of());
        pyrisConnectorService.executeFaqAdditionWebhook(toUpdateFaq, executionDTO);
        return jobToken;

    }

    /**
     * delete the faqs in pyris
     *
     * @param faq The faqs that gets erased
     * @return jobToken if the job was created
     */
    public String deleteFaq(Faq faq) {
        return executeFaqDeletionWebhook(new PyrisFaqWebhookDTO(faq.getId(), faq.getQuestionTitle(), faq.getQuestionAnswer(), faq.getCourse().getId(), faq.getCourse().getTitle(),
                faq.getCourse().getDescription()));

    }

    /**
     * executes the faq deletion webhook to delete faq from the vector database on pyris
     *
     * @param toUpdateFaqs The faq that got Updated as webhook DTO
     * @return jobToken if the job was created else null
     */
    private String executeFaqDeletionWebhook(PyrisFaqWebhookDTO toUpdateFaqs) {
        String jobToken = pyrisJobService.addFaqIngestionWebhookJob(0, 0);
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, artemisBaseUrl, "default");
        PyrisWebhookFaqDeletionExecutionDTO executionDTO = new PyrisWebhookFaqDeletionExecutionDTO(toUpdateFaqs, settingsDTO, List.of());
        pyrisConnectorService.executeFaqDeletionWebhook(executionDTO);
        return jobToken;
    }

    public IngestionState getFaqIngestionState(long courseId, long faqId) {
        return pyrisConnectorService.getFaqIngestionState(courseId, faqId);
    }
}
