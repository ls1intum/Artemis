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
