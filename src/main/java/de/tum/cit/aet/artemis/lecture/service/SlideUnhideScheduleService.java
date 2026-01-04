package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ScheduleService;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;
import de.tum.cit.aet.artemis.lecture.dto.SlideUnhideDTO;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Scheduler implementation that is only active on nodes with the CORE_AND_SCHEDULING profile.
 * This handles the actual scheduling of tasks using both the traditional scheduling mechanisms
 * and the integrated ScheduleService.
 */
@Conditional(LectureEnabled.class)
@Profile(PROFILE_CORE_AND_SCHEDULING)
@Lazy
@Service
public class SlideUnhideScheduleService {

    private final SlideRepository slideRepository;

    private final SlideUnhideExecutionService slideUnhideExecutionService;

    private final ScheduleService scheduleService;

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideScheduleService.class);

    public SlideUnhideScheduleService(SlideRepository slideRepository, SlideUnhideExecutionService slideUnhideExecutionService, ScheduleService scheduleService) {
        this.slideRepository = slideRepository;
        this.slideUnhideExecutionService = slideUnhideExecutionService;
        this.scheduleService = scheduleService;
    }

    /**
     * Method called when the bean has been created.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     * It loads all hidden slides and schedules tasks to unhide them at their expiration time.
     */
    @PostConstruct
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

        // Cancel any existing scheduled task for this slide
        cancelScheduledUnhiding(slideDTO.id());

        ZonedDateTime unhideDate = slideDTO.hidden();
        Instant unhideTime = unhideDate.toInstant();
        Instant now = Instant.now();

        if (unhideTime.isBefore(now)) {
            // If time has already passed, unhide immediately
            slideUnhideExecutionService.unhideSlide(slideDTO.id());
        }
        else {
            // Schedule for future unhiding using the full slide entity
            slideRepository.findById(slideDTO.id()).ifPresent(slide -> {
                scheduleService.scheduleSlideTask(slide, SlideLifecycle.UNHIDE, () -> slideUnhideExecutionService.unhideSlide(slide.getId()), "Slide Unhiding");
                log.debug("Scheduled slide {} to be unhidden at {}", slideDTO.id(), unhideDate);
            });
        }
    }

    /**
     * Fetches a slide by ID and schedules it for unhiding.
     *
     * @param slideId The ID of the slide to be scheduled for unhiding
     */
    public void scheduleSlideUnhiding(Long slideId) {
        slideRepository.findById(slideId).ifPresent(slide -> {
            SlideUnhideDTO slideDTO = new SlideUnhideDTO(slide.getId(), slide.getHidden());
            scheduleSlideUnhidingByDTO(slideDTO);
        });
    }

    /**
     * Cancels a scheduled unhiding task for a slide.
     *
     * @param slideId The ID of the slide whose task should be canceled
     */
    public void cancelScheduledUnhiding(Long slideId) {
        scheduleService.cancelScheduledTaskForSlideLifecycle(slideId, SlideLifecycle.UNHIDE);
        log.debug("Cancelled scheduled unhiding for slide {}", slideId);
    }
}
