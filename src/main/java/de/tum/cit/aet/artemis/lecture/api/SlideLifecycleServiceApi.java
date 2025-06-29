package de.tum.cit.aet.artemis.lecture.api;

import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.domain.SlideLifecycle;

/**
 * Service API for scheduling tasks at specific points in the lifecycle of a slide.
 */
public interface SlideLifecycleServiceApi extends AbstractApi {

    /**
     * Schedule a {@code Runnable} task in the lifecycle of a slide.
     *
     * @param slide         The slide for which the task is scheduled
     * @param lifecycleDate The date at which the task should be executed
     * @param lifecycle     The lifecycle event that triggers the task
     * @param task          The task to be executed
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task
     */
    ScheduledFuture<?> scheduleTask(Slide slide, ZonedDateTime lifecycleDate, SlideLifecycle lifecycle, Runnable task);

    /**
     * Schedule a {@code Runnable} task in the lifecycle of a slide.
     *
     * @param slide     The slide for which the task is scheduled
     * @param lifecycle The lifecycle event that triggers the task
     * @param task      The task to be executed
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task
     */
    ScheduledFuture<?> scheduleTask(Slide slide, SlideLifecycle lifecycle, Runnable task);
}
