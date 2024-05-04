package de.tum.in.www1.artemis.iris;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;
import de.tum.in.www1.artemis.service.connectors.pyris.job.IngestionWebhookJob;

@SpringBootTest
class PyrisWebhookServiceTest extends AbstractIrisIntegrationTest {

    @Autowired
    private PyrisJobService pyrisJobService;

    @Autowired
    private PyrisWebhookService pyrisWebhookService;

    @BeforeEach
    void setup() {
        when(pyrisJobService.addJob(any(IngestionWebhookJob.class))).thenReturn("dummyJobToken");
    }

    @Test
    void testExecuteIngestionPipelineWithAttachments() {
        // Given
        List<AttachmentUnit> attachmentUnits = List.of(createAttachmentUnit("PDF Attachment", AttachmentType.FILE), createAttachmentUnit("Video Attachment", AttachmentType.URL) // This
        // should
        // be
        // ignored
        );

        // When
        pyrisWebhookService.executeIngestionPipeline(true, attachmentUnits);

        // Then
        verify(pyrisJobService).addJob(any(IngestionWebhookJob.class));
    }

    @Test
    void testExecuteIngestionPipelineWithoutFileAttachments() {
        // Given
        List<AttachmentUnit> attachmentUnits = List.of(createAttachmentUnit("Video Attachment", AttachmentType.URL));

        // When
        pyrisWebhookService.executeIngestionPipeline(true, attachmentUnits);

        // Then
        verify(pyrisJobService, never()).addJob(any(IngestionWebhookJob.class)); // No job should be added
    }

    private AttachmentUnit createAttachmentUnit(String attachmentName, AttachmentType type) {
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(type);
        attachment.setLink("http://example.com/" + attachmentName);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setAttachment(attachment);
        return attachmentUnit;
    }
}
