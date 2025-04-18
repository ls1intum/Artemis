package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ScheduleService;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;

/**
 * Service for handling the business logic related to slide unhiding.
 */
@Profile(PROFILE_CORE)
@Service
public class SlideUnhideService {

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideService.class);

    private final SlideUnhideExecutionService slideUnhideExecutionService;

    private final Optional<ScheduleService> scheduleService;

    public SlideUnhideService(SlideUnhideExecutionService slideUnhideExecutionService, Optional<ScheduleService> scheduleService) {
        this.slideUnhideExecutionService = slideUnhideExecutionService;
        this.scheduleService = scheduleService;
    }

    /**
     * Processes a slide's hidden property update.
     * If the slide is marked as hidden with a future date, schedules an unhiding task.
     * If the slide is marked as hidden with a past date, unhides it immediately.
     * If the hidden property is null, cancels any existing unhiding tasks.
     *
     * @param slide The slide whose hidden property has been updated
     */
    public void handleSlideHiddenUpdate(Slide slide) {
        ZonedDateTime hiddenUntil = slide.getHidden();

        // Cancel any existing tasks first if schedule service is available
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
            log.debug("Scheduling unhiding of slide {} at {}", slide.getId(), hiddenUntil);
            scheduleService
                    .ifPresent(service -> service.scheduleSlideTask(slide, SlideLifecycle.UNHIDE, () -> slideUnhideExecutionService.unhideSlide(slide.getId()), "Slide Unhiding"));
        }
    }

    /**
     * Immediately unhides a slide regardless of its scheduled unhiding time.
     *
     * @param slideId The ID of the slide to unhide
     */
    public void unhideSlide(Long slideId) {
        log.debug("Manual unhiding of slide {}", slideId);
        scheduleService.ifPresent(service -> service.cancelScheduledTaskForSlideLifecycle(slideId, SlideLifecycle.UNHIDE));
        slideUnhideExecutionService.unhideSlide(slideId);
    }
}
