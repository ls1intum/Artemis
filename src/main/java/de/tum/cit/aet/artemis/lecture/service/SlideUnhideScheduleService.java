package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.FullStartupEvent;
import de.tum.cit.aet.artemis.lecture.dto.SlideUnhideDTO;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Scheduler implementation that is only active on nodes with the CORE_AND_SCHEDULING profile.
 * This handles the actual scheduling of tasks.
 */
@Profile(PROFILE_CORE_AND_SCHEDULING)
@Lazy
@Service
public class SlideUnhideScheduleService {

    private final SlideRepository slideRepository;

    private final SlideUnhideExecutionService slideUnhideExecutionService;

    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(SlideUnhideScheduleService.class);

    public SlideUnhideScheduleService(SlideRepository slideRepository, SlideUnhideExecutionService slideUnhideExecutionService, TaskScheduler taskScheduler) {
        this.slideRepository = slideRepository;
        this.slideUnhideExecutionService = slideUnhideExecutionService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Method called when the application is ready.
     * It loads all hidden slides and schedules tasks to unhide them at their expiration time.
     */
    @EventListener(FullStartupEvent.class)
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
            this.slideUnhideExecutionService.unhideSlide(slideDTO.id());
        }
        else {
            ScheduledFuture<?> scheduledTask = taskScheduler.schedule(() -> this.slideUnhideExecutionService.unhideSlide(slideDTO.id()), unhideTime);
            scheduledTasks.put(slideDTO.id(), scheduledTask);
            log.debug("Scheduled slide {} to be unhidden at {}", slideDTO.id(), unhideDate);
        }
    }

    /**
     * Fetches a slide by ID and schedules it for unhiding.
     * This method retrieves the slide from the repository, creates a SlideUnhideDTO,
     * and passes it to the scheduleSlideUnhiding method.
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
        ScheduledFuture<?> scheduledTask = scheduledTasks.get(slideId);
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTasks.remove(slideId);
            log.debug("Cancelled scheduled unhiding for slide {}", slideId);
        }
    }
}
