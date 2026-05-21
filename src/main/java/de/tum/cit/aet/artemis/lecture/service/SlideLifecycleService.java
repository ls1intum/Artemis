package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.api.SlideLifecycleServiceApi;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;

/**
 * Service implementation for scheduling tasks at specific points in the lifecycle of a slide.
 */
@Conditional(LectureEnabled.class)
@Service
@Lazy
public class SlideLifecycleService implements SlideLifecycleServiceApi {

    private static final Logger log = LoggerFactory.getLogger(SlideLifecycleService.class);

    private final TaskScheduler scheduler;

    public SlideLifecycleService(@Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public ScheduledFuture<?> scheduleTask(Slide slide, ZonedDateTime lifecycleDate, SlideLifecycle lifecycle, Runnable task) {
        final ScheduledFuture<?> future = scheduler.schedule(task, lifecycleDate.toInstant());
        log.debug("Scheduled Task for Slide (#{}) to trigger on {} - {}", slide.getId(), lifecycle, lifecycleDate);
        return future;
    }

    @Override
    public ScheduledFuture<?> scheduleTask(Slide slide, SlideLifecycle lifecycle, Runnable task) {
        return scheduleTask(slide, lifecycle.getDateFromSlide(slide), lifecycle, task);
    }
}
