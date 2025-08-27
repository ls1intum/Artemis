package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
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
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

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

    private boolean lectureIngestionEnabled(Course course) {
        return irisSettingsService.getCombinedIrisSettingsFor(course, true).irisLectureIngestionSettings().enabled();
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

        var settings = irisSettingsService.getCombinedIrisSettingsFor(course.get(), false).irisLectureIngestionSettings();
        if (!settings.enabled() || !settings.autoIngest()) {
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
        if (lectureIngestionEnabled(attachmentVideoUnit.getLecture().getCourse())) {
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
        var settings = irisSettingsService.getCombinedIrisSettingsFor(course, false).irisLectureIngestionSettings();
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, artemisBaseUrl, settings.selectedVariant());
        PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(toUpdateAttachmentVideoUnit,
                toUpdateAttachmentVideoUnit.lectureUnitId(), settingsDTO, List.of());
        pyrisConnectorService.executeLectureAdditionWebhook(executionDTO);
        return jobToken;
    }

    /**
     * uses getLectureUnitIngestionState for all lecture units and then determines the IngestionState of the lecture
     *
     * @param courseId id of the course
     * @return The ingestion state of the lecture
     *
     */
    public Map<Long, IngestionState> getLecturesIngestionState(long courseId) {
        LectureRepositoryApi api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        Set<Lecture> lectures = api.findAllByCourseId(courseId);
        return lectures.stream().collect(Collectors.toMap(DomainObject::getId, lecture -> getLectureIngestionState(courseId, lecture.getId())));

    }

    /**
     * uses getLectureUnitIngestionState for all lecture units and then determines the IngestionState of the lecture
     *
     * @param courseId  id of the course
     * @param lectureId id of the lecture
     * @return The ingestion state of the lecture
     *
     */
    private IngestionState getLectureIngestionState(long courseId, long lectureId) {
        Map<Long, IngestionState> states = getLectureUnitsIngestionState(courseId, lectureId);

        if (states.values().stream().allMatch(state -> state == IngestionState.DONE)) {
            return IngestionState.DONE;
        }

        if (states.values().stream().allMatch(state -> state == IngestionState.NOT_STARTED)) {
            return IngestionState.NOT_STARTED;
        }

        if (states.values().stream().allMatch(state -> state == IngestionState.ERROR)) {
            return IngestionState.ERROR;
        }

        if (states.containsValue(IngestionState.DONE) || states.containsValue(IngestionState.IN_PROGRESS)) {
            return IngestionState.PARTIALLY_INGESTED;
        }

        return IngestionState.NOT_STARTED;
    }

    /**
     * uses send an api call to get all the ingestion states of the lecture units of one lecture in Pyris
     *
     * @param courseId  id of the course
     * @param lectureId id of the lecture
     * @return The ingestion state of the lecture Unit
     */
    public Map<Long, IngestionState> getLectureUnitsIngestionState(long courseId, long lectureId) {
        LectureRepositoryApi api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
        List<LectureUnit> lectureunits = api.findByIdWithLectureUnitsElseThrow(lectureId).getLectureUnits();
        return lectureunits.stream().filter(lectureUnit -> lectureUnit instanceof AttachmentVideoUnit)
                .collect(Collectors.toMap(DomainObject::getId, unit -> pyrisConnectorService.getLectureUnitIngestionState(courseId, lectureId, unit.getId())));
    }

    private boolean faqIngestionEnabled(Course course) {
        var settings = irisSettingsService.getRawIrisSettingsFor(course).getIrisFaqIngestionSettings();
        return settings != null && settings.isEnabled();
    }

    /**
     * send the updated / created faqs to Pyris for ingestion if autoLecturesUpdate is enabled.
     *
     * @param newFaq the new faqs to be sent to pyris for ingestion
     */
    public void autoUpdateFaqInPyris(Faq newFaq) {
        var course = newFaq.getCourse();
        var settings = irisSettingsService.getCombinedIrisSettingsFor(course, false).irisFaqIngestionSettings();

        if (settings.enabled() && settings.autoIngest()) {
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
        if (faqIngestionEnabled(faq.getCourse())) {
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
        var settings = irisSettingsService.getCombinedIrisSettingsFor(course, false).irisFaqIngestionSettings();
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, artemisBaseUrl, settings.selectedVariant());
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
