package de.tum.in.www1.artemis.service.connectors.pyris;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.iris.settings.IrisCourseSettings;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSettingsRepository;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureDeletionExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.IngestionState;
import de.tum.in.www1.artemis.service.iris.exception.IrisInternalPyrisErrorException;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;

@Service
@Profile("iris")
public class PyrisWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PyrisWebhookService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final IrisSettingsService irisSettingsService;

    private final IrisSettingsRepository irisSettingsRepository;

    private final LectureUnitRepository lectureUnitRepository;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisWebhookService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            IrisSettingsRepository irisSettingsRepository, LectureUnitRepository lectureUnitRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.irisSettingsRepository = irisSettingsRepository;
        this.lectureUnitRepository = lectureUnitRepository;
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
        attachmentUnit.setPyrisIngestionState(IngestionState.IN_PROGRESS);
        lectureUnitRepository.save(attachmentUnit);
        return new PyrisLectureUnitWebhookDTO(base64EncodedPdf, lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle, courseDescription);
    }

    private PyrisLectureUnitWebhookDTO processAttachmentForDeletion(AttachmentUnit attachmentUnit) {
        Long lectureUnitId = attachmentUnit.getId();
        Long lectureId = attachmentUnit.getLecture().getId();
        Long courseId = attachmentUnit.getLecture().getCourse().getId();
        return new PyrisLectureUnitWebhookDTO("", lectureUnitId, "", lectureId, "", courseId, "", "");
    }

    /**
     * send the updated / created attachment to Pyris for ingestion if autoLecturesUpdate is enabled
     *
     * @param courseId           Id of the course where the attachment is added
     * @param newAttachmentUnits the new attachment Units to be sent to pyris for ingestion
     * @return true if the units were sent to pyris
     */
    public boolean autoUpdateAttachmentUnitsInPyris(Long courseId, List<AttachmentUnit> newAttachmentUnits) {
        IrisCourseSettings courseSettings = irisSettingsRepository.findCourseSettings(courseId).isPresent() ? irisSettingsRepository.findCourseSettings(courseId).get() : null;
        if (courseSettings != null && courseSettings.getIrisLectureIngestionSettings() != null && courseSettings.getIrisLectureIngestionSettings().isEnabled()
                && courseSettings.getIrisLectureIngestionSettings().getAutoIngestOnLectureAttachmentUpload()) {
            return addLectureUnitsToPyrisDB(newAttachmentUnits) != null;
        }
        return false;
    }

    /**
     * delete the lectures from the vector database on pyris
     *
     * @param attachmentUnits The attachmentUnit that got Updated / erased
     * @return jobToken if the job was created
     */
    public String deleteLectureFromPyrisDB(List<AttachmentUnit> attachmentUnits) {
        try {
            List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentUnits = new ArrayList<>();
            attachmentUnits.stream().filter(unit -> unit.getAttachment().getAttachmentType() == AttachmentType.FILE && unit.getAttachment().getLink().endsWith(".pdf"))
                    .forEach(unit -> {
                        toUpdateAttachmentUnits.add(processAttachmentForDeletion(unit));
                    });
            if (!toUpdateAttachmentUnits.isEmpty()) {
                return executeLectureDeletionWebhook(toUpdateAttachmentUnits);
            }
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    /**
     * adds the lectures to the vector database on pyris
     *
     * @param attachmentUnits The attachmentUnit that got Updated / erased
     * @return jobToken if the job was created
     */
    public String addLectureUnitsToPyrisDB(List<AttachmentUnit> attachmentUnits) {
        if (!lectureIngestionEnabled(attachmentUnits.getFirst().getLecture().getCourse())) {
            return null;
        }
        try {
            attachmentUnits.stream().filter(unit -> unit.getAttachment().getAttachmentType() == AttachmentType.FILE && unit.getAttachment().getLink().endsWith(".pdf"))
                    .forEach(unit -> {
                        executeLectureAdditionWebhook(processAttachmentForUpdate(unit));
                    });

        }
        catch (Exception e) {
            log.error(e.getMessage());
            attachmentUnits.stream().filter(unit -> unit.getAttachment().getAttachmentType() == AttachmentType.FILE && unit.getAttachment().getLink().endsWith(".pdf"))
                    .forEach(unit -> {
                        unit.setPyrisIngestionState(IngestionState.ERROR);
                        lectureUnitRepository.save(unit);
                    });
        }
        ;
        return null;
    }

    /**
     * executes executeLectureWebhook add or delete lectures from to the vector database on pyris
     *
     * @param toUpdateAttachmentUnits The attachmentUnit that are goin to be deleted
     * @return jobToken if the job was created
     */
    private String executeLectureDeletionWebhook(List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentUnits) {
        String jobToken = pyrisJobService.addIngestionWebhookJob();
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
        String jobToken = pyrisJobService.addIngestionWebhookJob();
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
        PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(toUpdateAttachmentUnit, settingsDTO, List.of());
        pyrisConnectorService.executeLectureAddtionWebhook("fullIngestion", executionDTO);
        return jobToken;
    }

}
