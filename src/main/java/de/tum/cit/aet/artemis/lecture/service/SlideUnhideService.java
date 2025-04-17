package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.lecture.domain.Slide;

/**
 * Service for managing slide unhiding operations in a multi-node environment.
 * This service handles the messaging aspects.
 */
@Profile(PROFILE_CORE)
@Service
public class SlideUnhideService {

    private final InstanceMessageSendService instanceMessageSendService;

    private final SlideUnhideExecutionService slideUnhideExecutionService;

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideService.class);

    public SlideUnhideService(InstanceMessageSendService instanceMessageSendService, SlideUnhideExecutionService slideUnhideExecutionService) {
        this.instanceMessageSendService = instanceMessageSendService;
        this.slideUnhideExecutionService = slideUnhideExecutionService;
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
     * Unhides a slide by delegating to the execution service.
     *
     * @param slideId The ID of the slide to unhide
     */
    public void unhideSlide(Long slideId) {
        slideUnhideExecutionService.unhideSlide(slideId);
    }
}
