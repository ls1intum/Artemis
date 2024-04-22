package de.tum.in.www1.artemis.service.connectors.pyris;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.repository.AttachmentRepository;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook.PyrisLectureUnitWebhookDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.lectureIngestionWebhook.PyrisWebhookLectureIngestionExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;

@Service
@Profile("iris")
public class PyrisWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PyrisWebhookService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    private final AttachmentRepository attachmentRepository;

    public PyrisWebhookService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, AttachmentRepository attachmentRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.attachmentRepository = attachmentRepository;

    }

    private String attachmentToBase64(AttachmentUnit attachmentUnit) {
        Path path = FilePathService.actualPathForPublicPathOrThrow(URI.create(attachmentUnit.getAttachment().getLink()));
        // Path path = Path.of(Optional.ofNullable(attachment).map(Attachment::getLink).orElse(""));
        try {
            byte[] fileBytes = Files.readAllBytes(path);

            return Base64.getEncoder().encodeToString(fileBytes);
        }
        catch (IOException e) {
            return e.getMessage();
        }
    }

    private List<PyrisLectureUnitWebhookDTO> processAttachments(List<AttachmentUnit> attachmentUnits) {
        return attachmentUnits.stream().map(attachmentUnit -> {
            try {
                int lectureUnitId = attachmentUnit.hashCode();
                String lectureUnitName = attachmentUnit.getName();
                int lectureId = attachmentUnit.getLecture().hashCode();
                String lectureTitle = attachmentUnit.getLecture().getTitle();
                int courseId = attachmentUnit.getLecture().getCourse().hashCode();
                String courseTitle = attachmentUnit.getLecture().getCourse().getTitle();
                String courseDescription = attachmentUnit.getLecture().getCourse().getDescription();
                String base64EncodedPdf = attachmentToBase64(attachmentUnit);
                return new PyrisLectureUnitWebhookDTO(base64EncodedPdf, lectureUnitId, lectureUnitName, lectureId, lectureTitle, courseId, courseTitle, courseDescription);
            }
            catch (Exception e) {
                log.error("Failed to process attachment for unit: {}", attachmentUnit.getName(), e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Scheduled(cron = "00 15 00 * * ?")
    public void executeIngestionPipelineAtScheduledTime() {
        PyrisWebhookLectureIngestionExecutionDTO executionDTO;
        var jobToken = pyrisJobService.addJob(new IngestionWebhookJob());
        var settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
        List<AttachmentUnit> updatedLectureUnits = attachmentRepository.findAllUpdatedAttachmentUnits();
        if (updatedLectureUnits.isEmpty()) {
            // Log that no updates were found
            log.info("No updated lecture units found. Using default values.");

            PyrisLectureUnitWebhookDTO pyrisLectureUnitWebhookDTO = new PyrisLectureUnitWebhookDTO("base64EncodedPdf", // Consider fetching from a config or constant
                    0, "lectureUnitName", 1, // Example lecture ID
                    "lectureTitle", 2, // Example course ID
                    "courseTitle", "courseDescription");
            // Using Collections.singletonList for immutability if no further modifications are needed
            var listDto = Collections.singletonList(pyrisLectureUnitWebhookDTO);
            executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(settingsDTO, listDto);

        }
        else {
            executionDTO = new PyrisWebhookLectureIngestionExecutionDTO(settingsDTO, processAttachments(updatedLectureUnits));
        }
        pyrisConnectorService.executeWebhook("lectures", executionDTO);
    }

}
