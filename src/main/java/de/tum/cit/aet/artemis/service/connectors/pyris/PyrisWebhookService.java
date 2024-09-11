package de.tum.cit.aet.artemis.service.connectors.pyris;

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

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.enumeration.AttachmentType;
import de.tum.cit.aet.artemis.domain.iris.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.domain.lecture.AttachmentUnit;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.cit.aet.artemis.service.iris.exception.IrisInternalPyrisErrorException;
import de.tum.cit.aet.artemis.service.iris.settings.IrisSettingsService;

@Service
@Profile("iris")
public class PyrisWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PyrisWebhookService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final IrisSettingsService irisSettingsService;

    private final IrisSettingsRepository irisSettingsRepository;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisWebhookService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService,
            IrisSettingsRepository irisSettingsRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
        this.irisSettingsRepository = irisSettingsRepository;
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
        return new PyrisLectureUnitWebhookDTO(true, artemisBaseUrl, base64EncodedPdf, lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle,
                courseDescription);
    }

    private PyrisLectureUnitWebhookDTO processAttachmentForDeletion(AttachmentUnit attachmentUnit) {
        Long lectureUnitId = attachmentUnit.getId();
        Long lectureId = attachmentUnit.getLecture().getId();
        Long courseId = attachmentUnit.getLecture().getCourse().getId();
        return new PyrisLectureUnitWebhookDTO(false, artemisBaseUrl, "", lectureUnitId, "", lectureId, "", courseId, "", "");
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
                return executeLectureWebhook(toUpdateAttachmentUnits);
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
            List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentUnits = new ArrayList<>();
            attachmentUnits.stream().filter(unit -> unit.getAttachment().getAttachmentType() == AttachmentType.FILE && unit.getAttachment().getLink().endsWith(".pdf"))
                    .forEach(unit -> {
                        toUpdateAttachmentUnits.add(processAttachmentForUpdate(unit));
                    });
            if (!toUpdateAttachmentUnits.isEmpty()) {
                return executeLectureWebhook(toUpdateAttachmentUnits);
            }
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    /**
     * executes executeLectureWebhook add or delete lectures from to the vector database on pyris
     *
     * @param toUpdateAttachmentUnits The attachmentUnit that are goin to be Updated / deleted
     * @return jobToken if the job was created
     */
    private String executeLectureWebhook(List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentUnits) {
        String jobToken = pyrisJobService.addIngestionWebhookJob();
        PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
        PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(toUpdateAttachmentUnits, settingsDTO, List.of());
        pyrisConnectorService.executeLectureWebhook("fullIngestion", executionDTO);
        return jobToken;
    }

}
