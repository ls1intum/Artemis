package de.tum.in.www1.artemis.service.scheduled;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.service.ExerciseLifecycleService;

@Service
public class ScheduleService {

    private final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private ExerciseLifecycleService exerciseLifecycleService;

    private final Map<Long, ScheduledFuture> scheduledTasks = new HashMap<>();

    public ScheduleService(ExerciseLifecycleService exerciseLifecycleService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
    }

    private void addScheduledTask(Exercise exercise, ScheduledFuture future) {
        scheduledTasks.put(exercise.getId(), future);
    }

    private void removeScheduledTask(Exercise exercise) {
        scheduledTasks.remove(exercise.getId());
    }

    /**
     * Schedule a task for the given Exercise for the provided ExerciseLifecycle.
     *
     * @param exercise Exercise
     * @param lifecycle ExerciseLifecycle
     * @param task Runnable task to be executed on the lifecycle hook
     */
    void scheduleTask(Exercise exercise, ExerciseLifecycle lifecycle, Runnable task) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled for clustering more than once.
        cancelScheduledTask(exercise);
        ScheduledFuture scheduledTask = exerciseLifecycleService.scheduleTask(exercise, lifecycle, task);
        addScheduledTask(exercise, scheduledTask);
    }

    /**
     * Cancel possible schedules tasks for a provided exercise.
     * @param exercise exercise for which a potential clustering task is canceled
     */
    void cancelScheduledTask(Exercise exercise) {
        ScheduledFuture future = scheduledTasks.get(exercise.getId());
        if (future != null) {
            log.debug("Cancelling scheduled task for build and test for student submissions after due date for Programming Exercise \"" + exercise.getTitle() + "\" (#"
                    + exercise.getId() + ").");
            future.cancel(false);
            removeScheduledTask(exercise);
        }
    }
}
