package de.tum.cit.aet.artemis.lecture.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Service for executing the core unhiding operations.
 * This is used by both SlideUnhideService and SlideUnhideScheduleService
 * to avoid circular dependencies.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Service
public class SlideUnhideExecutionService {

    private final SlideRepository slideRepository;

    private final AttachmentService attachmentService;

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideExecutionService.class);

    public SlideUnhideExecutionService(SlideRepository slideRepository, AttachmentService attachmentService) {
        this.slideRepository = slideRepository;
        this.attachmentService = attachmentService;
    }

    /**
     * Unhides a slide by setting its hidden property to null.
     * After unhiding, regenerates the student version of the attachment.
     *
     * @param slideId The ID of the slide to unhide
     */
    public void unhideSlide(Long slideId) {
        slideRepository.findById(slideId).ifPresent(slide -> {
            AttachmentVideoUnit attachmentVideoUnit = slide.getAttachmentVideoUnit();
            Attachment attachment = null;
            if (attachmentVideoUnit != null) {
                attachment = attachmentVideoUnit.getAttachment();
            }

            // Use repository method to handle transaction
            slideRepository.unhideSlide(slideId);
            log.debug("Unhid slide {}", slideId);

            // Regenerate student version of the attachment if applicable
            if (attachment != null) {
                try {
                    attachmentService.regenerateStudentVersion(attachment);
                }
                catch (Exception e) {
                    log.error("Failed to regenerate student version for attachment {} after unhiding slide {}: {}", attachment.getId(), slideId, e.getMessage(), e);
                }
            }
        });
    }
}
