package de.tum.cit.aet.artemis.lecture.service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

/**
 * Service that dynamically schedules tasks to unhide slides at their expiration time.
 */
@Service
public class SlideUnhideService implements ApplicationListener<ApplicationReadyEvent> {

    private final SlideRepository slideRepository;

    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private final ApplicationContext applicationContext;

    @Autowired
    public SlideUnhideService(SlideRepository slideRepository, TaskScheduler taskScheduler, ApplicationContext applicationContext) {
        this.slideRepository = slideRepository;
        this.taskScheduler = taskScheduler;
        this.applicationContext = applicationContext;
    }

    /**
     * Method called when the application is ready.
     * It loads all hidden slides and schedules tasks to unhide them at their expiration time.
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        SlideUnhideService self = applicationContext.getBean(SlideUnhideService.class);
        self.scheduleAllHiddenSlides();
    }

    /**
     * Loads all slides with a non-null hidden timestamp and schedules their unhiding.
     */
    @Transactional(readOnly = true)
    public void scheduleAllHiddenSlides() {
        List<Slide> hiddenSlides = slideRepository.findAllByHiddenNotNull();

        for (Slide slide : hiddenSlides) {
            scheduleSlideUnhiding(slide);
        }
    }

    /**
     * Schedules a task to unhide a specific slide at its expiration time.
     * If the expiration time has already passed, unhides it immediately.
     *
     * @param slide The slide to be unhidden
     */
    public void scheduleSlideUnhiding(Slide slide) {
        if (slide.getHidden() == null) {
            return;
        }

        // Cancel any existing scheduled task for this slide
        cancelScheduledUnhiding(slide.getId());

        Date unhideDate = slide.getHidden();
        Instant unhideTime = unhideDate.toInstant();
        Instant now = Instant.now();

        SlideUnhideService self = applicationContext.getBean(SlideUnhideService.class);

        if (unhideTime.isBefore(now)) {
            self.unhideSlide(slide.getId());
        }
        else {
            ScheduledFuture<?> scheduledTask = taskScheduler.schedule(() -> self.unhideSlide(slide.getId()), unhideTime);
            scheduledTasks.put(slide.getId(), scheduledTask);
        }
    }

    /**
     * Unhides a slide by setting its hidden property to null.
     *
     * @param slideId The ID of the slide to unhide
     */
    @Transactional
    public void unhideSlide(Long slideId) {
        slideRepository.findById(slideId).ifPresent(slide -> {
            slide.setHidden(null);
            slideRepository.save(slide);
            scheduledTasks.remove(slideId);
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
        }
    }

    /**
     * This method should be called whenever a slide's hidden timestamp is updated,
     * to make sure the scheduling is adjusted accordingly.
     *
     * @param slide The slide with updated hidden timestamp
     */
    public void handleSlideHiddenUpdate(Slide slide) {
        if (slide.getHidden() != null) {
            scheduleSlideUnhiding(slide);
        }
        else {
            cancelScheduledUnhiding(slide.getId());
        }
    }
}
