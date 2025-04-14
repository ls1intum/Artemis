package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Service for managing slide unhiding operations in a multi-node environment.
 * This service has two parts:
 * - Core functionality (available on all nodes with PROFILE_CORE)
 * - Scheduling functionality (only available on nodes with PROFILE_CORE_AND_SCHEDULING)
 */
@Profile(PROFILE_CORE)
@Service
public class SlideUnhideService {

    private final SlideRepository slideRepository;

    private final AttachmentService attachmentService;

    private final InstanceMessageSendService instanceMessageSendService;

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideService.class);

    public SlideUnhideService(SlideRepository slideRepository, AttachmentService attachmentService, @Lazy InstanceMessageSendService instanceMessageSendService) {
        this.slideRepository = slideRepository;
        this.attachmentService = attachmentService;
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * This method should be called whenever a slide's hidden timestamp is updated,
     * to make sure the scheduling is adjusted accordingly.
     *
     * @param slide The slide with updated hidden timestamp
     */
    public void handleSlideHiddenUpdate(Slide slide) {
        if (slide.getHidden() != null) {
            // Send message to scheduling node to schedule unhiding
            instanceMessageSendService.sendSlideUnhideSchedule(slide.getId());
            log.debug("Sent slide unhide schedule message for slide {} with unhide time {}", slide.getId(), slide.getHidden());
        }
        else {
            // Send message to cancel any existing scheduled tasks
            instanceMessageSendService.sendSlideUnhideScheduleCancel(slide.getId());
            log.debug("Sent slide unhide cancel message for slide {}", slide.getId());
        }
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
