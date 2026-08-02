package de.tum.cit.aet.artemis.lecture.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class StudentVersionRegenerationService {

    private static final Logger log = LoggerFactory.getLogger(StudentVersionRegenerationService.class);

    private static final int RETRY_BATCH_SIZE = 50;

    private final AttachmentRepository attachmentRepository;

    private final AttachmentService attachmentService;

    private final AtomicLong retryCursor = new AtomicLong();

    public StudentVersionRegenerationService(AttachmentRepository attachmentRepository, AttachmentService attachmentService) {
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
    }

    /**
     * Regenerates a bounded batch of student PDF versions that are still pending after a previous failure.
     *
     * @param event the scheduled retry event
     */
    @EventListener
    public void handleRetryPendingStudentVersions(StudentVersionRegenerationScheduleService.RetryPendingStudentVersionsEvent event) {
        List<Attachment> attachments = findNextBatch();
        for (Attachment attachment : attachments) {
            retryCursor.set(attachment.getId());
            try {
                attachmentService.regenerateStudentVersion(attachment);
            }
            catch (Exception exception) {
                log.error("Failed to retry student version regeneration for attachment {}: {}", attachment.getId(), exception.getMessage(), exception);
            }
        }
    }

    private List<Attachment> findNextBatch() {
        List<Attachment> attachments = attachmentRepository.findAllRequiringStudentVersionRegeneration(retryCursor.get(), PageRequest.ofSize(RETRY_BATCH_SIZE));
        if (attachments.isEmpty() && retryCursor.getAndSet(0) != 0) {
            attachments = attachmentRepository.findAllRequiringStudentVersionRegeneration(0, PageRequest.ofSize(RETRY_BATCH_SIZE));
        }
        return attachments;
    }
}
