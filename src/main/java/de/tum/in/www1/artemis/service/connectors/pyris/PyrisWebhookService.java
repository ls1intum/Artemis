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
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook.PyrisLectureUnitWebhookDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;

@Service
@Profile("iris")
public class PyrisWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PyrisWebhookService.class);

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
            return e.getMessage();
        }
    }

    private PyrisLectureUnitWebhookDTO processAttachments(Boolean shouldUpdate, AttachmentUnit attachmentUnit) {
        try {
            if (shouldUpdate) {
                int lectureUnitId = attachmentUnit.hashCode();
                String lectureUnitName = attachmentUnit.getName();
                int lectureId = attachmentUnit.getLecture().hashCode();
                String lectureTitle = attachmentUnit.getLecture().getTitle();
                int courseId = attachmentUnit.getLecture().getCourse().hashCode();
                String courseTitle = attachmentUnit.getLecture().getCourse().getTitle();
                String courseDescription = attachmentUnit.getLecture().getCourse().getDescription() == null ? "" : attachmentUnit.getLecture().getCourse().getDescription();
                String base64EncodedPdf = attachmentToBase64(attachmentUnit);
                return new PyrisLectureUnitWebhookDTO(true, base64EncodedPdf, lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle, courseDescription);
            }
            else {
                int lectureUnitId = attachmentUnit.hashCode();
                int lectureId = attachmentUnit.getLecture().hashCode();
                int courseId = attachmentUnit.getLecture().getCourse().hashCode();
                return new PyrisLectureUnitWebhookDTO(false, "", lectureUnitId, "", lectureId, "", courseId, "", "");
            }
        }
        catch (Exception e) {
            log.error("Failed to process attachment for unit: {}", attachmentUnit.getName(), e);
            return null;
        }
    }

    /**
     * Exeßcutes the tutor chat pipeline for the given session
     *
     * @param shouldUpdate    True if the lecture is updated, False if the lecture is erased
     * @param attachmentUnits The attachmentUnit that got Updated / erased
     */
    public void executeLectureIngestionPipeline(Boolean shouldUpdate, List<AttachmentUnit> attachmentUnits) {
        if (lectureIngestionEnabled(attachmentUnits.getFirst().getLecture().getCourse())) {
            var jobToken = pyrisJobService.addIngestionWebhookJob();
            var settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
            List<PyrisLectureUnitWebhookDTO> toUpdateAttachmentUnits = new ArrayList<>();
            for (AttachmentUnit attachmentUnit : attachmentUnits) {
                if (attachmentUnit.getAttachment().getAttachmentType() == AttachmentType.FILE) {
                    toUpdateAttachmentUnits.add(processAttachments(shouldUpdate, attachmentUnit));
                }
            }
            if (!toUpdateAttachmentUnits.isEmpty()) {
                PyrisWebhookLectureIngestionExecutionDTO executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(toUpdateAttachmentUnits, settingsDTO, List.of());
                pyrisConnectorService.executeLectureWebhook("fullIngestion", executionDTO);
            }
        }

    }
}
