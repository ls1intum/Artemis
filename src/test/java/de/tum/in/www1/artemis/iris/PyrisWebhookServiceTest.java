package de.tum.in.www1.artemis.iris;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;

class PyrisWebhookServiceTest extends AbstractIrisIntegrationTest {

    @Autowired
    private PyrisWebhookService pyrisWebhookService;

    @Autowired
    private PyrisJobService pyrisJobService;

    @BeforeEach
    void setup() {
        when(pyrisJobService.addJob(any())).thenReturn("dummyJobToken");
    }

    @Test
    void testExecuteIngestionPipelineWithAttachments() {
        // Given
        List<AttachmentUnit> attachmentUnits = List.of(createAttachmentUnit("PDF Attachment", AttachmentType.FILE));

        // When
        pyrisWebhookService.executeIngestionPipeline(true, attachmentUnits);

        // Verify that job is added since we cannot directly test executeWebhook
        verify(pyrisJobService).addJob(any());
    }

    @Test
    void testExecuteIngestionPipelineWithoutAttachments() {
        // Given
        List<AttachmentUnit> attachmentUnits = List.of(createAttachmentUnit("Video Attachment", AttachmentType.URL));

        // When
        pyrisWebhookService.executeIngestionPipeline(true, attachmentUnits);

        // As there are no FILE type attachments, no jobs should be added
        verify(pyrisJobService, never()).addJob(any());
    }

    private AttachmentUnit createAttachmentUnit(String attachmentName, AttachmentType type) {
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(type);
        attachment.setLink("http://example.com/" + attachmentName);
        attachmentUnit.setAttachment(attachment);
        return attachmentUnit;
    }
}
