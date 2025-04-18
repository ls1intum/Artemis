package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;

/**
 * Service for scheduling tasks at specific points in the lifecycle of a slide.
 */
@Profile(PROFILE_CORE)
@Service
public class SlideLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SlideLifecycleService.class);

    private final TaskScheduler scheduler;

    public SlideLifecycleService(@Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Schedule a {@code Runnable} task in the lifecycle of a slide. ({@code SlideLifecycle})
     * Tasks are performed in a background thread managed by a {@code TaskScheduler}.
     * <b>Important:</b> Scheduled tasks are not persisted across application restarts.
     *
     * @param slide         The slide for which the task is scheduled
     * @param lifecycleDate The date at which the task should be executed
     * @param lifecycle     The lifecycle event that triggers the task
     * @param task          The task to be executed
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task or check whether it has been executed.
     */
    public ScheduledFuture<?> scheduleTask(Slide slide, ZonedDateTime lifecycleDate, SlideLifecycle lifecycle, Runnable task) {
        final ScheduledFuture<?> future = scheduler.schedule(task, lifecycleDate.toInstant());
        log.debug("Scheduled Task for Slide (#{}) to trigger on {} - {}", slide.getId(), lifecycle, lifecycleDate);
        return future;
    }

    /**
     * Schedule a {@code Runnable} task in the lifecycle of a slide. ({@code SlideLifecycle})
     * Tasks are performed in a background thread managed by a {@code TaskScheduler}.
     * <b>Important:</b> Scheduled tasks are not persisted across application restarts.
     *
     * @param slide     The slide for which the task is scheduled
     * @param lifecycle The lifecycle event that triggers the task
     * @param task      The task to be executed
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task or check whether it has been executed.
     */
    public ScheduledFuture<?> scheduleTask(Slide slide, SlideLifecycle lifecycle, Runnable task) {
        return scheduleTask(slide, lifecycle.getDateFromSlide(slide), lifecycle, task);
    }
}
