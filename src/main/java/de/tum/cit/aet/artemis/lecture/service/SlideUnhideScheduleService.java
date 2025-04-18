package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ScheduleService;
import de.tum.cit.aet.artemis.lecture.dto.SlideUnhideDTO;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Scheduler implementation that is only active on nodes with the CORE_AND_SCHEDULING profile.
 * This handles the actual scheduling of tasks using the integrated ScheduleService.
 */
@Profile(PROFILE_CORE_AND_SCHEDULING)
@Service
public class SlideUnhideScheduleService {

    private final SlideRepository slideRepository;

    private final SlideUnhideService slideUnhideService;

    private final ScheduleService scheduleService;

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideScheduleService.class);

    public SlideUnhideScheduleService(SlideRepository slideRepository, SlideUnhideService slideUnhideService, ScheduleService scheduleService) {
        this.slideRepository = slideRepository;
        this.slideUnhideService = slideUnhideService;
        this.scheduleService = scheduleService;
    }

    /**
     * Method called when the application is ready.
     * It loads all hidden slides and schedules tasks to unhide them at their expiration time.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        scheduleAllHiddenSlides();
    }

    /**
     * Loads all slides with a non-null hidden timestamp and schedules their unhiding.
     */
    public void scheduleAllHiddenSlides() {
        List<SlideUnhideDTO> hiddenSlidesProjection = slideRepository.findHiddenSlidesProjection();
        log.debug("Scheduling {} hidden slides for unhiding", hiddenSlidesProjection.size());

        for (SlideUnhideDTO slideDTO : hiddenSlidesProjection) {
            scheduleSlideUnhidingByDTO(slideDTO);
        }
    }

    /**
     * Schedules a task to unhide a specific slide at its expiration time.
     * If the expiration time has already passed, unhides it immediately.
     *
     * @param slideDTO The slide DTO containing id and hidden datetime
     */
    public void scheduleSlideUnhidingByDTO(SlideUnhideDTO slideDTO) {
        if (slideDTO.hidden() == null) {
            return;
        }

        slideRepository.findById(slideDTO.id()).ifPresent(slideUnhideService::handleSlideHiddenUpdate);
    }

    /**
     * Fetches a slide by ID and schedules it for unhiding.
     *
     * @param slideId The ID of the slide to be scheduled for unhiding
     */
    public void scheduleSlideUnhiding(Long slideId) {
        slideRepository.findById(slideId).ifPresent(slideUnhideService::handleSlideHiddenUpdate);
    }

    /**
     * Cancels a scheduled unhiding task for a slide.
     *
     * @param slideId The ID of the slide whose task should be canceled
     */
    public void cancelScheduledUnhiding(Long slideId) {
        scheduleService.cancelAllScheduledSlideTasks(slideId);
        log.debug("Cancelled scheduled unhiding for slide {}", slideId);
    }
}
