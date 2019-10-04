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
import de.tum.in.www1.artemis.service.util.Tuple;

@Service
public class ScheduleService {

    private final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private ExerciseLifecycleService exerciseLifecycleService;

    private final Map<Tuple<Long, ExerciseLifecycle>, ScheduledFuture> scheduledTasks = new HashMap<>();

    public ScheduleService(ExerciseLifecycleService exerciseLifecycleService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
    }

    private void addScheduledTask(Exercise exercise, ExerciseLifecycle lifecycle, ScheduledFuture future) {
        Tuple<Long, ExerciseLifecycle> taskId = new Tuple<>(exercise.getId(), lifecycle);
        scheduledTasks.put(taskId, future);
    }

    private void removeScheduledTask(Exercise exercise, ExerciseLifecycle lifecycle) {
        Tuple<Long, ExerciseLifecycle> taskId = new Tuple<>(exercise.getId(), lifecycle);
        scheduledTasks.remove(taskId);
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
        cancelScheduledTaskForLifecycle(exercise, lifecycle);
        ScheduledFuture scheduledTask = exerciseLifecycleService.scheduleTask(exercise, lifecycle, task);
        addScheduledTask(exercise, lifecycle, scheduledTask);
    }

    /**
     * Cancel possible schedules tasks for a provided exercise.
     * @param exercise exercise for which a potential clustering task is canceled
     */
    void cancelScheduledTaskForLifecycle(Exercise exercise, ExerciseLifecycle lifecycle) {
        Tuple<Long, ExerciseLifecycle> taskId = new Tuple<>(exercise.getId(), lifecycle);
        ScheduledFuture future = scheduledTasks.get(taskId);
        if (future != null) {
            log.debug("Cancelling scheduled task for Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ").");
            future.cancel(false);
            removeScheduledTask(exercise, lifecycle);
        }
    }
}
