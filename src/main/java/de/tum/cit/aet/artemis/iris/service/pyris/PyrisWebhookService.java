package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.ARTEMIS_FILE_PATH_PREFIX;
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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
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
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcriptionIngestion.PyrisTranscriptionIngestionWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcriptionIngestion.PyrisWebhookTranscriptionDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.transcriptionIngestion.PyrisWebhookTranscriptionIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
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

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisWebhookService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
    }

    /**
     * adds the transcription to the vector database in Pyris
     *
     * @param transcription The transcription that got Updated
     * @param course        The course of the transcriptions
     * @param lecture       The lecture of the transcriptions
     * @param lectureUnit   The lecture unit of the transcriptions
     * @return jobToken if the job was created else null
     */
    public String addTranscriptionsToPyrisDB(LectureTranscription transcription, Course course, Lecture lecture, AttachmentVideoUnit lectureUnit) {
        if (transcription == null) {
            throw new IllegalArgumentException("Transcriptions cannot be empty");
        }

        if (!lectureIngestionEnabled(course)) {
            return null;
        }

        if (transcription.getLectureUnit().getLecture() == null) {
            throw new IllegalArgumentException("Transcription must be associated with a lecture");
        }
        else if (!transcription.getLectureUnit().getLecture().equals(lecture)) {
            throw new IllegalArgumentException("All transcriptions must be associated with the same lecture");
        }

        PyrisTranscriptionIngestionWebhookDTO pyrisTranscriptionIngestionWebhookDTO = new PyrisTranscriptionIngestionWebhookDTO(transcription, lecture.getId(), lecture.getTitle(),
                course.getId(), course.getTitle(), course.getDescription(), transcription.getLectureUnit().getId(), transcription.getLectureUnit().getName(),
                lectureUnit.getVideoSource());

        return executeTranscriptionAdditionWebhook(pyrisTranscriptionIngestionWebhookDTO, course, lecture, lectureUnit);
    }

    /**
     * adds the lecture transcription into the vector database of Pyris
     *
     * @param toUpdateTranscription The transcription that is going to be Updated
     * @return jobToken if the job was created
     */
    private String executeTranscriptionAdditionWebhook(PyrisTranscriptionIngestionWebhookDTO toUpdateTranscription, Course course, Lecture lecture, LectureUnit lectureUnit) {
        String jobToken = pyrisJobService.addTranscriptionIngestionWebhookJob(course.getId(), lecture.getId(), lectureUnit.getId());
        var settings = irisSettingsService.getCombinedIrisSettingsFor(course, false).irisLectureIngestionSettings();
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, artemisBaseUrl, settings.selectedVariant());
        PyrisWebhookTranscriptionIngestionExecutionDTO executionDTO = new PyrisWebhookTranscriptionIngestionExecutionDTO(toUpdateTranscription, lectureUnit.getId(), settingsDTO,
                List.of());
        pyrisConnectorService.executeTranscriptionAdditionWebhook(executionDTO);
        return jobToken;
    }

    /**
     * delete the lecture transcription in pyris
     *
     * @param lectureTranscription The lecture transcription that gets erased
     * @return jobToken if the job was created
     */
    public String deleteLectureTranscription(LectureTranscription lectureTranscription) {
        Lecture lecture = lectureTranscription.getLectureUnit().getLecture();
        Course course = lecture.getCourse();
        LectureUnit lectureUnit = lectureTranscription.getLectureUnit();
        if (!(lectureUnit instanceof AttachmentVideoUnit)) {
            throw new IllegalArgumentException("Lecture Transcription must belong to an AttachmentVideoUnit");
        }
        return executeLectureTranscriptionDeletionWebhook(new PyrisTranscriptionIngestionWebhookDTO(lectureTranscription, lecture.getId(), lecture.getTitle(), course.getId(),
                course.getTitle(), course.getDescription(), lectureUnit.getId(), lectureUnit.getName(), ((AttachmentVideoUnit) lectureUnit).getVideoSource()));
    }

    /**
     * executes the lecture transcription deletion webhook to delete lecture transcriptions from the vector database on pyris
     *
     * @param toUpdateLectureTranscription The lecture transcription that got Updated as webhook DTO
     * @return jobToken if the job was created else null
     */
    private String executeLectureTranscriptionDeletionWebhook(PyrisTranscriptionIngestionWebhookDTO toUpdateLectureTranscription) {
        String jobToken = pyrisJobService.addTranscriptionIngestionWebhookJob(toUpdateLectureTranscription.courseId(), toUpdateLectureTranscription.lectureId(),
                toUpdateLectureTranscription.transcription().getLectureUnit().getId());
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, artemisBaseUrl, "default");
        PyrisWebhookTranscriptionDeletionExecutionDTO executionDTO = new PyrisWebhookTranscriptionDeletionExecutionDTO(toUpdateLectureTranscription, settingsDTO, List.of());
        pyrisConnectorService.executeLectureTranscriptionDeletionWebhook(executionDTO);

        return jobToken;
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

    private PyrisLectureUnitWebhookDTO processAttachmentForUpdate(AttachmentVideoUnit attachmentVideoUnit) {
        Long lectureUnitId = attachmentVideoUnit.getId();
        String lectureUnitName = attachmentVideoUnit.getName();
        Long lectureId = attachmentVideoUnit.getLecture().getId();
        String lectureTitle = attachmentVideoUnit.getLecture().getTitle();
        Long courseId = attachmentVideoUnit.getLecture().getCourse().getId();
        String courseTitle = attachmentVideoUnit.getLecture().getCourse().getTitle();
        String courseDescription = attachmentVideoUnit.getLecture().getCourse().getDescription() == null ? "" : attachmentVideoUnit.getLecture().getCourse().getDescription();
        String base64EncodedPdf = attachmentToBase64(attachmentVideoUnit);
        String lectureUnitLink = artemisBaseUrl + ARTEMIS_FILE_PATH_PREFIX + attachmentVideoUnit.getAttachment().getLink();
        LectureUnitRepositoryApi api = lectureUnitRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureUnitRepositoryApi.class));
        api.save(attachmentVideoUnit);
        return new PyrisLectureUnitWebhookDTO(base64EncodedPdf, lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle, courseDescription, lectureUnitLink);
    }

    private PyrisLectureUnitWebhookDTO processAttachmentForDeletion(AttachmentVideoUnit attachmentVideoUnit) {
        Long lectureUnitId = attachmentVideoUnit.getId();
        Long lectureId = attachmentVideoUnit.getLecture().getId();
        Long courseId = attachmentVideoUnit.getLecture().getCourse().getId();
        return new PyrisLectureUnitWebhookDTO("", lectureUnitId, "", lectureId, "", courseId, "", "", "");
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
            addLectureUnitToPyrisDB(attachmentVideoUnit);
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
        attachmentVideoUnits.stream().filter(unit -> unit.getAttachment().getAttachmentType() == AttachmentType.FILE && unit.getAttachment().getLink().endsWith(".pdf"))
                .forEach(unit -> toUpdateAttachmentVideoUnits.add(processAttachmentForDeletion(unit)));
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
        if (lectureIngestionEnabled(attachmentVideoUnit.getLecture().getCourse()) && attachmentVideoUnit.getAttachment() != null
                && attachmentVideoUnit.getAttachment().getAttachmentType() == AttachmentType.FILE && attachmentVideoUnit.getAttachment().getLink().endsWith(".pdf")) {
            return executeLectureAdditionWebhook(processAttachmentForUpdate(attachmentVideoUnit), attachmentVideoUnit.getLecture().getCourse());
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
        PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(toUpdateAttachmentVideoUnit, settingsDTO, List.of());
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
