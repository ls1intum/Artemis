package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ScheduleService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;

/**
 * Service for managing slide unhiding operations in a multi-node environment.
 * This service handles both the messaging aspects and the integration with ScheduleService.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SlideUnhideService {

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideService.class);

    private final InstanceMessageSendService instanceMessageSendService;

    private final SlideUnhideExecutionService slideUnhideExecutionService;

    private final Optional<ScheduleService> scheduleService;

    public SlideUnhideService(InstanceMessageSendService instanceMessageSendService, SlideUnhideExecutionService slideUnhideExecutionService,
            Optional<ScheduleService> scheduleService) {
        this.instanceMessageSendService = instanceMessageSendService;
        this.slideUnhideExecutionService = slideUnhideExecutionService;
        this.scheduleService = scheduleService;
    }

    /**
     * Processes a slide's hidden property update.
     * If the slide is marked as hidden with a future date, sends a message to schedule an unhiding task.
     * If the slide is marked as hidden with a past date, unhides it immediately.
     * If the hidden property is null, sends a message to cancel any existing unhiding tasks.
     *
     * @param slide The slide whose hidden property has been updated
     */
    public void handleSlideHiddenUpdate(Slide slide) {
        ZonedDateTime hiddenUntil = slide.getHidden();

        // Cancel any existing tasks through the messaging service
        instanceMessageSendService.sendSlideUnhideScheduleCancel(slide.getId());

        // Also cancel locally if schedule service is available
        scheduleService.ifPresent(service -> service.cancelScheduledTaskForSlideLifecycle(slide.getId(), SlideLifecycle.UNHIDE));

        if (hiddenUntil == null) {
            log.debug("Slide {} is not hidden, no need to schedule unhiding", slide.getId());
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();

        if (hiddenUntil.isBefore(now)) {
            log.debug("Slide {} should already be unhidden, unhiding now", slide.getId());
            slideUnhideExecutionService.unhideSlide(slide.getId());
        }
        else {
            // Send message to scheduling node to schedule unhiding
            instanceMessageSendService.sendSlideUnhideSchedule(slide.getId());
            log.debug("Sent slide unhide schedule message for slide {} with unhide time {}", slide.getId(), hiddenUntil);

            // Also schedule locally if schedule service is available and we're on a scheduling node
            scheduleService
                    .ifPresent(service -> service.scheduleSlideTask(slide, SlideLifecycle.UNHIDE, () -> slideUnhideExecutionService.unhideSlide(slide.getId()), "Slide Unhiding"));
        }
    }
}
