package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;

@Service
public class ExerciseLifecycleService {

    private final Logger log = LoggerFactory.getLogger(ExerciseLifecycleService.class);

    private TaskScheduler scheduler;

    ExerciseLifecycleService(@Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Allow to schedule a {@code Runnable} task in the lifecycle of an exercise. ({@code ExerciseLifecycle}) Tasks are performed in a background thread managed by a
     * {@code TaskScheduler}. See {@code TaskSchedulingConfiguration}. <b>Important:</b> Scheduled tasks are not persisted accross application restarts. Therefore, schedule your
     * events from both your application logic (e.g. exercise modification) and on application startup. You can use the {@code PostConstruct} Annotation to call one service method
     * on startup.
     *
     * @param exercise  Exercise
     * @param lifecycle ExerciseLifecycle
     * @param task      Runnable
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task or check whether it has been executed.
     */
    public ScheduledFuture<?> scheduleTask(Exercise exercise, ExerciseLifecycle lifecycle, Runnable task) {
        final ZonedDateTime lifecycleDate;

        switch (lifecycle) {
        case RELEASE:
            lifecycleDate = exercise.getReleaseDate();
            break;

        case DUE:
            lifecycleDate = exercise.getDueDate();
            break;

        case ASSESSMENT_DUE:
            lifecycleDate = exercise.getAssessmentDueDate();
            break;

        default:
            throw new IllegalStateException("Unexpected Exercise Lifecycle State: " + lifecycle);
        }

        final ScheduledFuture<?> future = scheduler.schedule(task, lifecycleDate.toInstant());
        log.debug("Scheduled Task for Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") to trigger on " + lifecycle.toString() + ".");
        return future;
    }

}
