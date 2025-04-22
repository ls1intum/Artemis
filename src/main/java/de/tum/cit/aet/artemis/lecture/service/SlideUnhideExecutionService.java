package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Service for executing the core unhiding operations.
 * This is used by both SlideUnhideService and SlideUnhideScheduleService
 * to avoid circular dependencies.
 */
@Profile(PROFILE_CORE)
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
            AttachmentUnit attachmentUnit = slide.getAttachmentUnit();
            Attachment attachment = null;
            if (attachmentUnit != null) {
                attachment = attachmentUnit.getAttachment();
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
