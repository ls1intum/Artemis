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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisFaqWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook.PyrisWebhookFaqIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureDeletionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Service
@Profile(PROFILE_IRIS)
public class PyrisWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PyrisWebhookService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final IrisSettingsService irisSettingsService;

    private final IrisSettingsRepository irisSettingsRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisWebhookService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            IrisSettingsRepository irisSettingsRepository, LectureUnitRepository lectureUnitRepository, LectureRepository lectureRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.irisSettingsRepository = irisSettingsRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
    }

    private boolean lectureIngestionEnabled(Course course) {
        return irisSettingsService.getRawIrisSettingsFor(course).getIrisLectureIngestionSettings() != null
                && irisSettingsService.getRawIrisSettingsFor(course).getIrisLectureIngestionSettings().isEnabled();
    }

    private String attachmentToBase64(AttachmentUnit attachmentUnit) {
        Path path = FilePathService.actualPathForPublicPathOrThrow(URI.create(attachmentUnit.getAttachment().getLink()));
        try {
            byte[] fileBytes = Files.readAllBytes(path);
            return Base64.getEncoder().encodeToString(fileBytes);
        }
        catch (IOException e) {
            throw new IrisInternalPyrisErrorException(e.getMessage());
        }
    }

    private PyrisLectureUnitWebhookDTO processAttachmentForUpdate(AttachmentUnit attachmentUnit) {
        Long lectureUnitId = attachmentUnit.getId();
        String lectureUnitName = attachmentUnit.getName();
        Long lectureId = attachmentUnit.getLecture().getId();
        String lectureTitle = attachmentUnit.getLecture().getTitle();
        Long courseId = attachmentUnit.getLecture().getCourse().getId();
        String courseTitle = attachmentUnit.getLecture().getCourse().getTitle();
        String courseDescription = attachmentUnit.getLecture().getCourse().getDescription() == null ? "" : attachmentUnit.getLecture().getCourse().getDescription();
        String base64EncodedPdf = attachmentToBase64(attachmentUnit);
        String lectureUnitLink = artemisBaseUrl + attachmentUnit.getAttachment().getLink();
        lectureUnitRepository.save(attachmentUnit);
        return new PyrisLectureUnitWebhookDTO(base64EncodedPdf, lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle, courseDescription, lectureUnitLink);
    }

    private PyrisLectureUnitWebhookDTO processAttachmentForDeletion(AttachmentUnit attachmentUnit) {
        Long lectureUnitId = attachmentUnit.getId();
        Long lectureId = attachmentUnit.getLecture().getId();
        Long courseId = attachmentUnit.getLecture().getCourse().getId();
        return new PyrisLectureUnitWebhookDTO("", lectureUnitId, "", lectureId, "", courseId, "", "", "");
    }

    /**
     * send the updated / created attachment to Pyris for ingestion if autoLecturesUpdate is enabled
     *
     * @param courseId           Id of the course where the attachment is added
     * @param newAttachmentUnits the new attachment Units to be sent to pyris for ingestion
     */
    public void autoUpdateAttachmentUnitsInPyris(Long courseId, List<AttachmentUnit> newAttachmentUnits) {
        IrisCourseSettings courseSettings = irisSettingsRepository.findCourseSettings(courseId).isPresent() ? irisSettingsRepository.findCourseSettings(courseId).get() : null;
        if (courseSettings != null && courseSettings.getIrisLectureIngestionSettings() != null && courseSettings.getIrisLectureIngestionSettings().isEnabled()
                && courseSettings.getIrisLectureIngestionSettings().getAutoIngestOnLectureAttachmentUpload()) {
            for (AttachmentUnit attachmentUnit : newAttachmentUnits) {
                addLectureUnitToPyrisDB(attachmentUnit);
            }
        }
    }

    /**
     * delete the lectures from the vector database on pyris
     *
     * @param attachmentUnits The attachmentUnit that got Updated / erased
     * @return jobToken if the job was created
     */
    public String deleteLectureFromPyrisDB(List<AttachmentUnit> attachmentUnits) {
        List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentUnits = new ArrayList<>();
        attachmentUnits.stream().filter(unit -> unit.getAttachment().getAttachmentType() == AttachmentType.FILE && unit.getAttachment().getLink().endsWith(".pdf"))
                .forEach(unit -> {
                    toUpdateAttachmentUnits.add(processAttachmentForDeletion(unit));
                });
        if (!toUpdateAttachmentUnits.isEmpty()) {
            return executeLectureDeletionWebhook(toUpdateAttachmentUnits);
        }
        return null;
    }

    /**
     * adds the lectures to the vector database in Pyris
     *
     * @param attachmentUnit The attachmentUnit that got Updated
     * @return jobToken if the job was created else null
     */
    public String addLectureUnitToPyrisDB(AttachmentUnit attachmentUnit) {
        if (lectureIngestionEnabled(attachmentUnit.getLecture().getCourse())) {
            if (attachmentUnit.getAttachment().getAttachmentType() == AttachmentType.FILE && attachmentUnit.getAttachment().getLink().endsWith(".pdf")) {
                return executeLectureAdditionWebhook(processAttachmentForUpdate(attachmentUnit));
            }
            log.error("Attachment {} is not a file or is not of type pdf thus it will not be sent to Pyris", attachmentUnit.getId());
        }
        return null;
    }

    /**
     * executes executeLectureWebhook add or delete lectures from to the vector database on pyris
     *
     * @param toUpdateAttachmentUnits The attachmentUnit that are goin to be deleted
     * @return jobToken if the job was created
     */
    private String executeLectureDeletionWebhook(List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentUnits) {
        String jobToken = pyrisJobService.addLectureIngestionWebhookJob(0, 0, 0);
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
        PyrisWebhookLectureDeletionExecutionDTO executionDTO = new PyrisWebhookLectureDeletionExecutionDTO(toUpdateAttachmentUnits, settingsDTO, List.of());
        pyrisConnectorService.executeLectureDeletionWebhook(executionDTO);
        return jobToken;
    }

    /**
     * executes executeLectureAdditionWebhook add lecture from to the vector database on pyris
     *
     * @param toUpdateAttachmentUnit The attachmentUnit that are going to be Updated
     * @return jobToken if the job was created
     */
    private String executeLectureAdditionWebhook(PyrisLectureUnitWebhookDTO toUpdateAttachmentUnit) {
        String jobToken = pyrisJobService.addLectureIngestionWebhookJob(toUpdateAttachmentUnit.courseId(), toUpdateAttachmentUnit.lectureId(),
                toUpdateAttachmentUnit.lectureUnitId());
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
        PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(toUpdateAttachmentUnit, settingsDTO, List.of());
        pyrisConnectorService.executeLectureAddtionWebhook("fullIngestion", executionDTO);
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
        Set<Lecture> lectures = lectureRepository.findAllByCourseId(courseId);
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
        List<LectureUnit> lectureunits = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureId).getLectureUnits();
        return lectureunits.stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit)
                .collect(Collectors.toMap(DomainObject::getId, unit -> pyrisConnectorService.getLectureUnitIngestionState(courseId, lectureId, unit.getId())));
    }

    private boolean faqIngestionEnabled(Course course) {
        var settings = irisSettingsService.getRawIrisSettingsFor(course).getIrisFaqIngestionSettings();
        return settings != null && settings.isEnabled();
    }

    /**
     * send the updated / created faqs to Pyris for ingestion if autoLecturesUpdate is enabled.
     *
     * @param courseId Id of the course where the attachment is added
     * @param newFaq   the new faqs to be sent to pyris for ingestion
     */
    public void autoUpdateFaqInPyris(Long courseId, Faq newFaq) {
        IrisCourseSettings presentCourseSettings = null;
        Optional<IrisCourseSettings> courseSettings = irisSettingsRepository.findCourseSettings(courseId);
        if (courseSettings.isPresent()) {
            presentCourseSettings = courseSettings.get();
        }

        if (presentCourseSettings != null && presentCourseSettings.getIrisFaqIngestionSettings() != null && presentCourseSettings.getIrisFaqIngestionSettings().isEnabled()
                && presentCourseSettings.getIrisFaqIngestionSettings().getAutoIngestOnFaqCreation()) {
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
                    faq.getCourse().getTitle(), faq.getCourse().getDescription()));
        }
        return null;
    }

    /**
     * executes the faq addition webhook to add faq to the vector database on pyris
     *
     * @param toUpdateFaq The faq that got Updated as webhook DTO
     * @return jobToken if the job was created else null
     */

    private String executeFaqAdditionWebhook(PyrisFaqWebhookDTO toUpdateFaq) {
        String jobToken = pyrisJobService.addFaqIngestionWebhookJob(toUpdateFaq.courseId(), toUpdateFaq.faqId());
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
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
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
        PyrisWebhookFaqDeletionExecutionDTO executionDTO = new PyrisWebhookFaqDeletionExecutionDTO(toUpdateFaqs, settingsDTO, List.of());
        pyrisConnectorService.executeFaqDeletionWebhook(executionDTO);
        return jobToken;
    }

    public IngestionState getFaqIngestionState(long courseId, long faqId) {
        return pyrisConnectorService.getFaqIngestionState(courseId, faqId);
    }

}
