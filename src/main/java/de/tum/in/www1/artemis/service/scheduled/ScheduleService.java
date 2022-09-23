package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationLifecycle;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.service.ExerciseLifecycleService;
import de.tum.in.www1.artemis.service.ParticipationLifecycleService;
import de.tum.in.www1.artemis.service.util.Tuple;

@Service
public class ScheduleService {

    private final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ExerciseLifecycleService exerciseLifecycleService;

    private final ParticipationLifecycleService participationLifecycleService;

    private final Map<Tuple<Long, ExerciseLifecycle>, Set<ScheduledFuture<?>>> scheduledExerciseTasks = new HashMap<>();

    // triple of exercise id, participation id, and lifecycle
    private final Map<Triple<Long, Long, ParticipationLifecycle>, Set<ScheduledFuture<?>>> scheduledParticipationTasks = new HashMap<>();

    public ScheduleService(ExerciseLifecycleService exerciseLifecycleService, ParticipationLifecycleService participationLifecycleService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
        this.participationLifecycleService = participationLifecycleService;
    }

    private void addScheduledTask(Exercise exercise, ExerciseLifecycle lifecycle, Set<ScheduledFuture<?>> futures) {
        Tuple<Long, ExerciseLifecycle> taskId = new Tuple<>(exercise.getId(), lifecycle);
        scheduledExerciseTasks.put(taskId, futures);
    }

    private void removeScheduledTask(Long exerciseId, ExerciseLifecycle lifecycle) {
        Tuple<Long, ExerciseLifecycle> taskId = new Tuple<>(exerciseId, lifecycle);
        scheduledExerciseTasks.remove(taskId);
    }

    private void addScheduledTask(Participation participation, ParticipationLifecycle lifecycle, Set<ScheduledFuture<?>> futures) {
        Triple<Long, Long, ParticipationLifecycle> taskId = Triple.of(participation.getExercise().getId(), participation.getId(), lifecycle);
        scheduledParticipationTasks.put(taskId, futures);
    }

    private void removeScheduledTask(Long exerciseId, Long participationId, ParticipationLifecycle lifecycle) {
        Triple<Long, Long, ParticipationLifecycle> taskId = Triple.of(exerciseId, participationId, lifecycle);
        scheduledParticipationTasks.remove(taskId);
    }

    /**
     * Schedule a task for the given Exercise for the provided ExerciseLifecycle.
     *
     * @param exercise Exercise
     * @param lifecycle ExerciseLifecycle
     * @param task Runnable task to be executed on the lifecycle hook
     */
    public void scheduleTask(Exercise exercise, ExerciseLifecycle lifecycle, Runnable task) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled more than once.
        cancelScheduledTaskForLifecycle(exercise.getId(), lifecycle);
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
    public void scheduleTask(Exercise exercise, ExerciseLifecycle lifecycle, Set<Tuple<ZonedDateTime, Runnable>> tasks) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled more than once.
        cancelScheduledTaskForLifecycle(exercise.getId(), lifecycle);
        Set<ScheduledFuture<?>> scheduledTasks = exerciseLifecycleService.scheduleMultipleTasks(exercise, lifecycle, tasks);
        addScheduledTask(exercise, lifecycle, scheduledTasks);
    }

    /**
     * Schedule a task for the given participation for the provided lifecycle.
     *
     * @param participation for which a scheduled action should be created.
     * @param lifecycle at which the task should be scheduled.
     * @param task Runnable task to be executed on the lifecycle hook
     */
    public void scheduleParticipationTask(Participation participation, ParticipationLifecycle lifecycle, Runnable task) {
        cancelScheduledTaskForParticipationLifecycle(participation.getExercise().getId(), participation.getId(), lifecycle);
        participationLifecycleService.scheduleTask(participation, lifecycle, task).ifPresent(scheduledTask -> addScheduledTask(participation, lifecycle, Set.of(scheduledTask)));
    }

    /**
     * Cancel possible schedules tasks for a provided exercise.
     *
     * Additionally, cancels the tasks for participations of that exercise for the corresponding {@link ParticipationLifecycle}.
     *
     * @param exerciseId if of the exercise for which a potential scheduled task is canceled
     */
    public void cancelScheduledTaskForLifecycle(Long exerciseId, ExerciseLifecycle lifecycle) {
        Tuple<Long, ExerciseLifecycle> taskId = new Tuple<>(exerciseId, lifecycle);
        Set<ScheduledFuture<?>> futures = scheduledExerciseTasks.get(taskId);
        if (futures != null) {
            log.debug("Cancelling scheduled task {} for Exercise (#{}).", lifecycle, exerciseId);
            futures.forEach(future -> future.cancel(true));
            removeScheduledTask(exerciseId, lifecycle);
        }

        ParticipationLifecycle.fromExerciseLifecycle(lifecycle).ifPresent(participationLifecycle -> {
            final Stream<Long> participationIds = getScheduledParticipationIdsForExercise(exerciseId);
            participationIds.forEach(participationId -> cancelScheduledTaskForParticipationLifecycle(exerciseId, participationId, participationLifecycle));
        });
    }

    /**
     * Finds all individual participations that belong to the given exercise and are scheduled.
     *
     * @param exerciseId the participations belong to.
     * @return a stream of the IDs of participations.
     */
    private Stream<Long> getScheduledParticipationIdsForExercise(Long exerciseId) {
        return scheduledParticipationTasks.keySet().stream().map(Triple::getLeft).filter(scheduledExerciseId -> Objects.equals(scheduledExerciseId, exerciseId));
    }

    /**
     * Cancel possible schedules tasks for a provided participation.
     *
     * @param participationId of the participation for which a potential scheduled task is cancelled.
     */
    public void cancelScheduledTaskForParticipationLifecycle(Long exerciseId, Long participationId, ParticipationLifecycle lifecycle) {
        Triple<Long, Long, ParticipationLifecycle> taskId = Triple.of(exerciseId, participationId, lifecycle);
        Set<ScheduledFuture<?>> futures = scheduledParticipationTasks.get(taskId);
        if (futures != null) {
            log.debug("Cancelling scheduled task {} for Participation (#{}).", lifecycle, participationId);
            futures.forEach(future -> future.cancel(true));
            removeScheduledTask(exerciseId, participationId, lifecycle);
        }
    }

    /**
     * Cancels all scheduled tasks for all {@link ParticipationLifecycle ParticipationLifecycles} for the given participation.
     *
     * @param exerciseId of the exercise the participation belongs to.
     * @param participationId of the participation itself.
     */
    void cancelAllScheduledParticipationTasks(Long exerciseId, Long participationId) {
        for (final ParticipationLifecycle lifecycle : ParticipationLifecycle.values()) {
            cancelScheduledTaskForParticipationLifecycle(exerciseId, participationId, lifecycle);
        }
    }
}
