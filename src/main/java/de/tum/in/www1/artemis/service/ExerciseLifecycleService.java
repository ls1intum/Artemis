package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.service.util.Tuple;

@Service
public class ExerciseLifecycleService {

    private final Logger log = LoggerFactory.getLogger(ExerciseLifecycleService.class);

    private final TaskScheduler scheduler;

    public ExerciseLifecycleService(@Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Allow to schedule a {@code Runnable} task in the lifecycle of an exercise. ({@code ExerciseLifecycle}) Tasks are performed in a background thread managed by a
     * {@code TaskScheduler}. See {@code TaskSchedulingConfiguration}. <b>Important:</b> Scheduled tasks are not persisted across application restarts. Therefore, schedule your
     * events from both your application logic (e.g. exercise modification) and on application startup. You can use the {@code PostConstruct} Annotation to call one service method
     * on startup.
     *
     * @param exercise  Exercise
     * @param lifecycle ExerciseLifecycle
     * @param task      Runnable
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task or check whether it has been executed.
     */
    public ScheduledFuture<?> scheduleTask(Exercise exercise, ExerciseLifecycle lifecycle, Runnable task) {
        final ZonedDateTime lifecycleDate = lifecycle.getDateFromExercise(exercise);
        final ScheduledFuture<?> future = scheduler.schedule(task, lifecycleDate.toInstant());
        log.debug("Scheduled Task for Exercise \"{}\" (#{}) to trigger on {}.", exercise.getTitle(), exercise.getId(), lifecycle);
        return future;
    }

    /**
     * Allow scheduling multiple {@code Runnable} tasks in the lifecycle of an exercise at distinct points in time. ({@code ExerciseLifecycle}) Tasks are performed in a
     * background thread managed by a {@code TaskScheduler}. See {@code TaskSchedulingConfiguration}. <b>Important:</b> Scheduled tasks are not persisted across application
     * restarts. Therefore, schedule your events from both your application logic (e.g. exercise modification) and on application startup. You can use the {@code PostConstruct}
     * Annotation to call one service method on startup.
     *
     * @param exercise  Exercise
     * @param lifecycle ExerciseLifecycle
     * @param tasks     Runnable with ZonedDateTime
     * @return The {@code ScheduledFuture<?>}s allow to later cancel the tasks or check whether they have been executed.
     */
    public Set<ScheduledFuture<?>> scheduleMultipleTasks(Exercise exercise, ExerciseLifecycle lifecycle, Set<Tuple<ZonedDateTime, Runnable>> tasks) {
        final Set<ScheduledFuture<?>> futures = new HashSet<>();
        for (var task : tasks) {
            var future = scheduler.schedule(task.y(), task.x().toInstant());
            futures.add(future);
        }
        log.debug("Scheduled {} Tasks for Exercise \"{}\" (#{}) to trigger on {}.", tasks.size(), exercise.getTitle(), exercise.getId(), lifecycle.toString());
        return futures;
    }
}
