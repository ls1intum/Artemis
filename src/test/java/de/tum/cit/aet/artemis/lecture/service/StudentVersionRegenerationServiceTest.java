package de.tum.cit.aet.artemis.lecture.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;

class StudentVersionRegenerationServiceTest {

    @Test
    void advancesPastFailedAttachmentsBeforeWrappingToTheFirstBatch() {
        AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        var firstAttachment = attachment(10L);
        var secondAttachment = attachment(20L);
        when(attachmentRepository.findAllRequiringStudentVersionRegeneration(eq(0L), any(Pageable.class))).thenReturn(List.of(firstAttachment));
        when(attachmentRepository.findAllRequiringStudentVersionRegeneration(eq(10L), any(Pageable.class))).thenReturn(List.of(secondAttachment));
        when(attachmentRepository.findAllRequiringStudentVersionRegeneration(eq(20L), any(Pageable.class))).thenReturn(List.of());
        doThrow(new IllegalStateException("permanent failure")).when(attachmentService).regenerateStudentVersion(firstAttachment);
        var service = new StudentVersionRegenerationService(attachmentRepository, attachmentService);
        var event = new StudentVersionRegenerationScheduleService.RetryPendingStudentVersionsEvent();

        service.handleRetryPendingStudentVersions(event);
        service.handleRetryPendingStudentVersions(event);
        service.handleRetryPendingStudentVersions(event);

        var inOrder = inOrder(attachmentService);
        inOrder.verify(attachmentService).regenerateStudentVersion(firstAttachment);
        inOrder.verify(attachmentService).regenerateStudentVersion(secondAttachment);
        inOrder.verify(attachmentService).regenerateStudentVersion(firstAttachment);
        verify(attachmentRepository).findAllRequiringStudentVersionRegeneration(eq(10L), any(Pageable.class));
    }

    private static Attachment attachment(long id) {
        var attachment = new Attachment();
        attachment.setId(id);
        return attachment;
    }
}
