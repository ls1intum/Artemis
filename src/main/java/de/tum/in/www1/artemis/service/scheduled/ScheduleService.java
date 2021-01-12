package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

    private final ExerciseLifecycleService exerciseLifecycleService;

    private final Map<Tuple<Long, ExerciseLifecycle>, Set<ScheduledFuture<?>>> scheduledTasks = new HashMap<>();

    public ScheduleService(ExerciseLifecycleService exerciseLifecycleService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
    }

    private void addScheduledTask(Exercise exercise, ExerciseLifecycle lifecycle, Set<ScheduledFuture<?>> futures) {
        Tuple<Long, ExerciseLifecycle> taskId = new Tuple<>(exercise.getId(), lifecycle);
        scheduledTasks.put(taskId, futures);
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
        // no exercise should be scheduled more than once.
        cancelScheduledTaskForLifecycle(exercise, lifecycle);
        ScheduledFuture<?> scheduledTask = exerciseLifecycleService.scheduleTask(exercise, lifecycle, task);
        addScheduledTask(exercise, lifecycle, Set.of(scheduledTask));
    }

    /**
     * Schedule a set of tasks for the given Exercise for the provided ExerciseLifecycle at the given times.
     *
     * @param exercise Exercise
     * @param lifecycle ExerciseLifecycle
     * @param tasks Runnable tasks to be executed at the associated ZonedDateTimes
     */
    void scheduleTask(Exercise exercise, ExerciseLifecycle lifecycle, Set<Tuple<ZonedDateTime, Runnable>> tasks) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled more than once.
        cancelScheduledTaskForLifecycle(exercise, lifecycle);
        Set<ScheduledFuture<?>> scheduledTasks = exerciseLifecycleService.scheduleMultipleTasks(exercise, lifecycle, tasks);
        addScheduledTask(exercise, lifecycle, scheduledTasks);
    }

    /**
     * Cancel possible schedules tasks for a provided exercise.
     * @param exercise exercise for which a potential clustering task is canceled
     */
    void cancelScheduledTaskForLifecycle(Exercise exercise, ExerciseLifecycle lifecycle) {
        Tuple<Long, ExerciseLifecycle> taskId = new Tuple<>(exercise.getId(), lifecycle);
        Set<ScheduledFuture<?>> futures = scheduledTasks.get(taskId);
        if (futures != null) {
            log.debug("Cancelling scheduled task " + lifecycle + " for Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ").");
            futures.forEach(future -> future.cancel(false));
            removeScheduledTask(exercise, lifecycle);
        }
    }
}
