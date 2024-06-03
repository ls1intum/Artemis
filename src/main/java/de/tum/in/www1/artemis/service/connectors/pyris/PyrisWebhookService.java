package de.tum.in.www1.artemis.service.connectors.pyris;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisLectureUnitWebhookDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureingestionwebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.in.www1.artemis.service.iris.exception.IrisInternalPyrisErrorException;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;

@Service
@Profile("iris")
public class PyrisWebhookService {

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final IrisSettingsService irisSettingsService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisWebhookService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, IrisSettingsService irisSettingsService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.irisSettingsService = irisSettingsService;
    }

    private boolean lectureIngestionEnabled(Course course) {
        return Boolean.TRUE.equals(irisSettingsService.getRawIrisSettingsFor(course).getIrisLectureIngestionSettings().isEnabled());
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
        int lectureUnitId = attachmentUnit.hashCode();
        String lectureUnitName = attachmentUnit.getName();
        int lectureId = attachmentUnit.getLecture().hashCode();
        String lectureTitle = attachmentUnit.getLecture().getTitle();
        int courseId = attachmentUnit.getLecture().getCourse().hashCode();
        String courseTitle = attachmentUnit.getLecture().getCourse().getTitle();
        String courseDescription = attachmentUnit.getLecture().getCourse().getDescription() == null ? "" : attachmentUnit.getLecture().getCourse().getDescription();
        String base64EncodedPdf = attachmentToBase64(attachmentUnit);
        return new PyrisLectureUnitWebhookDTO(true, artemisBaseUrl, base64EncodedPdf, lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle,
                courseDescription);
    }

    private PyrisLectureUnitWebhookDTO processAttachmentForDeletion(AttachmentUnit attachmentUnit) {
        int lectureUnitId = attachmentUnit.hashCode();
        int lectureId = attachmentUnit.getLecture().hashCode();
        int courseId = attachmentUnit.getLecture().getCourse().hashCode();
        return new PyrisLectureUnitWebhookDTO(false, artemisBaseUrl, "", lectureUnitId, "", lectureId, "", courseId, "", "");
    }

    /**
     * Executes the Lecture Ingestion pipeline for the given
     *
     * @param shouldUpdate    True if the lecture is updated, False if the lecture is erased
     * @param attachmentUnits The attachmentUnit that got Updated / erased
     */
    public void executeLectureIngestionPipeline(Boolean shouldUpdate, List<AttachmentUnit> attachmentUnits) {
        if (lectureIngestionEnabled(attachmentUnits.getFirst().getLecture().getCourse())) {
            List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentUnits = new ArrayList<>();
            attachmentUnits.stream().filter(unit -> unit.getAttachment().getAttachmentType() == AttachmentType.FILE).forEach(unit -> {
                if (shouldUpdate) {
                    toUpdateAttachmentUnits.add(processAttachmentForUpdate(unit));
                }
                else {
                    toUpdateAttachmentUnits.add(processAttachmentForDeletion(unit));
                }
            });
            if (!toUpdateAttachmentUnits.isEmpty()) {
                String jobToken = pyrisJobService.addIngestionWebhookJob();
                PyrisPipelineExecutionSettingsDTO settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
                PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(toUpdateAttachmentUnits, settingsDTO, List.of());
                pyrisConnectorService.executeLectureWebhook("fullIngestion", executionDTO);
            }
        }
    }
}
