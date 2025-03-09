package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZoneId.systemDefault;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.Pair;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseLifecycleService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationLifecycleService;
import de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@Profile(PROFILE_CORE)
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ExerciseLifecycleService exerciseLifecycleService;

    private final ParticipationLifecycleService participationLifecycleService;

    record ExerciseLifecycleKey(Long exerciseId, ExerciseLifecycle lifecycle) {
    }

    record ParticipationLifecycleKey(Long exerciseId, Long participationId, ParticipationLifecycle lifecycle) {
    }

    record ScheduledTaskName(ScheduledFuture<?> future, String name) {
    }

    public record ScheduledExerciseEvent(Long exerciseId, ExerciseLifecycle lifecycle, String name, ZonedDateTime scheduledTime, Future.State state) {
    }

    private final ConcurrentMap<ExerciseLifecycleKey, Set<ScheduledTaskName>> scheduledExerciseTasks = new ConcurrentHashMap<>();

    // triple of exercise id, participation id, and lifecycle
    private final ConcurrentMap<ParticipationLifecycleKey, Set<ScheduledTaskName>> scheduledParticipationTasks = new ConcurrentHashMap<>();

    private final TaskScheduler taskScheduler;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm:ss");

    public ScheduleService(ExerciseLifecycleService exerciseLifecycleService, ParticipationLifecycleService participationLifecycleService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
        this.participationLifecycleService = participationLifecycleService;

        // Initialize the TaskScheduler
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setVirtualThreads(true);
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    /**
     * Get all scheduled exercise events.
     *
     * @param pageable the pagination information
     * @return a page of scheduled exercise events
     */
    public Page<ScheduledExerciseEvent> findAllExerciseEvents(Pageable pageable) {
        // Flatten the map into a list of ScheduledExerciseEvent
        var allEvents = scheduledExerciseTasks.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(task -> {
            // Calculate the scheduled time from the future's delay
            var scheduledTime = ZonedDateTime.now().plusSeconds(task.future().getDelay(TimeUnit.SECONDS));
            return new ScheduledExerciseEvent(entry.getKey().exerciseId(), entry.getKey().lifecycle(), task.name(), scheduledTime, task.future().state());
        })).sorted(Comparator.comparing(ScheduledExerciseEvent::scheduledTime)).toList();

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allEvents.size());
        List<ScheduledExerciseEvent> paginatedEvents = start < allEvents.size() ? allEvents.subList(start, end) : new ArrayList<>();

        return new PageImpl<>(paginatedEvents, pageable, allEvents.size());
    }

    /**
     * Initializes and schedules periodic logging and cleanup tasks for scheduled exercises
     * and participation tasks. This method is triggered automatically when the application
     * is fully started.
     *
     * <p>
     * Every 15 seconds, this method:
     * <ul>
     * <li>Logs the total number of scheduled exercise and participation tasks.</li>
     * <li>Iterates through scheduled exercise tasks and logs their details, including
     * execution time and state. It removes tasks that are no longer running.</li>
     * <li>Iterates through scheduled participation tasks and logs their details,
     * including execution time and state. It removes tasks that are no longer running.</li>
     * <li>Cleans up empty entries from both task maps to avoid memory leaks.</li>
     * </ul>
     *
     * <p>
     * The scheduling mechanism ensures that outdated or completed tasks do not persist in
     * memory unnecessarily while maintaining visibility into scheduled tasks.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startup() {
        taskScheduler.scheduleAtFixedRate(() -> {
            log.debug("Number of scheduled Exercise Tasks: {}", scheduledExerciseTasks.values().stream().mapToLong(Set::size).sum());

            // if the map is not empty and there is at least still one future in the values map, log the tasks and remove the ones that are not running anymore
            if (!scheduledExerciseTasks.isEmpty() && scheduledExerciseTasks.values().stream().anyMatch(set -> !set.isEmpty())) {
                log.debug("  Scheduled Exercise Tasks:");
                scheduledExerciseTasks.forEach((key, taskNames) -> {
                    taskNames.removeIf(taskName -> {
                        long delay = taskName.future().getDelay(TimeUnit.SECONDS);
                        var state = taskName.future().state();
                        Instant scheduledTime = Instant.now().plusSeconds(delay);
                        ZonedDateTime zonedScheduledTime = scheduledTime.atZone(systemDefault());
                        String formattedTime = zonedScheduledTime.format(formatter);
                        log.debug("    Exercise: {}, Lifecycle: {}, Name: {}, Scheduled Run Time: {}, State: {}, Remaining Delay: {} s", key.exerciseId(), key.lifecycle(),
                                taskName.name(), formattedTime, state, delay);
                        return state != Future.State.RUNNING;
                    });
                });
            }

            // clean up empty entries in the map
            scheduledExerciseTasks.entrySet().removeIf(entry -> entry.getValue().isEmpty());

            log.debug("Number of scheduled Participation Tasks: {}", scheduledParticipationTasks.values().stream().mapToLong(Set::size).sum());

            // if the map is not empty and there is at least still one future in the values map, log the tasks and remove the ones that are not running anymore
            if (!scheduledParticipationTasks.isEmpty() && scheduledParticipationTasks.values().stream().anyMatch(set -> !set.isEmpty())) {
                log.debug("  Scheduled Participation Tasks:");
                scheduledParticipationTasks.forEach((key, taskNames) -> {
                    taskNames.removeIf(taskName -> {
                        long delay = taskName.future().getDelay(TimeUnit.SECONDS);
                        var state = taskName.future().state();
                        Instant scheduledTime = Instant.now().plusSeconds(delay);
                        ZonedDateTime zonedScheduledTime = scheduledTime.atZone(systemDefault());
                        String formattedTime = zonedScheduledTime.format(formatter);
                        log.debug("    Exercise: {}, Participation: {}, Lifecycle: {}, Name: {}, Scheduled Run Time: {}, State: {}, Remaining Delay: {} s", key.exerciseId(),
                                key.participationId(), key.lifecycle(), taskName.name(), formattedTime, state, delay);
                        return state != Future.State.RUNNING;
                    });
                });
            }

            // clean up empty entries in the map
            scheduledParticipationTasks.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        }, Duration.ofSeconds(15));
    }

    private void addScheduledExerciseTasks(Exercise exercise, ExerciseLifecycle lifecycle, Set<ScheduledFuture<?>> futures, String name) {
        ExerciseLifecycleKey task = new ExerciseLifecycleKey(exercise.getId(), lifecycle);
        scheduledExerciseTasks.put(task, convert(futures, name));
    }

    private Set<ScheduledTaskName> convert(Set<ScheduledFuture<?>> futures, String name) {
        return futures.stream().map(future -> new ScheduledTaskName(future, name)).collect(Collectors.toSet());
    }

    private void removeScheduledExerciseTask(Long exerciseId, ExerciseLifecycle lifecycle) {
        ExerciseLifecycleKey task = new ExerciseLifecycleKey(exerciseId, lifecycle);
        scheduledExerciseTasks.remove(task);
    }

    private void addScheduledParticipationTask(Participation participation, ParticipationLifecycle lifecycle, Set<ScheduledFuture<?>> futures, String name) {
        ParticipationLifecycleKey task = new ParticipationLifecycleKey(participation.getExercise().getId(), participation.getId(), lifecycle);
        scheduledParticipationTasks.put(task, convert(futures, name));
    }

    private void removeScheduledParticipationTask(Long exerciseId, Long participationId, ParticipationLifecycle lifecycle) {
        ParticipationLifecycleKey task = new ParticipationLifecycleKey(exerciseId, participationId, lifecycle);
        scheduledParticipationTasks.remove(task);
    }

    /**
     * Schedule a task for the given Exercise for the provided ExerciseLifecycle.
     *
     * @param exercise  Exercise
     * @param lifecycle ExerciseLifecycle
     * @param task      Runnable task to be executed on the lifecycle hook
     * @param name      Name of the task
     */
    public void scheduleExerciseTask(Exercise exercise, ExerciseLifecycle lifecycle, Runnable task, String name) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled more than once.
        cancelScheduledTaskForLifecycle(exercise.getId(), lifecycle);
        ScheduledFuture<?> scheduledTask = exerciseLifecycleService.scheduleTask(exercise, lifecycle, task);
        addScheduledExerciseTasks(exercise, lifecycle, new HashSet<>(List.of(scheduledTask)), name);
    }

    /**
     * Schedule a task for the given QuizExercise for the provided ExerciseLifecycle.
     *
     * @param exercise  QuizExercise
     * @param batch     QuizBatch
     * @param lifecycle ExerciseLifecycle
     * @param task      Runnable task to be executed on the lifecycle hook
     * @param name      Name of the task
     */
    public void scheduleExerciseTask(QuizExercise exercise, QuizBatch batch, ExerciseLifecycle lifecycle, Runnable task, String name) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled more than once.
        cancelScheduledTaskForLifecycle(exercise.getId(), lifecycle);
        ScheduledFuture<?> scheduledTask = exerciseLifecycleService.scheduleTask(exercise, batch, lifecycle, task);
        addScheduledExerciseTasks(exercise, lifecycle, new HashSet<>(List.of(scheduledTask)), name);
    }

    /**
     * Schedule a set of tasks for the given Exercise for the provided ExerciseLifecycle at the given times.
     *
     * @param exercise      Exercise
     * @param lifecycle     ExerciseLifecycle
     * @param scheduledTask One runnable tasks to be executed at the associated ZonedDateTimes
     * @param name          Name of the task
     */
    public void scheduleExerciseTask(Exercise exercise, ExerciseLifecycle lifecycle, Pair<ZonedDateTime, Runnable> scheduledTask, String name) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled more than once for each lifecycle
        cancelScheduledTaskForLifecycle(exercise.getId(), lifecycle);
        ScheduledFuture<?> scheduledFuture = exerciseLifecycleService.scheduleTask(exercise, lifecycle, scheduledTask.second());
        addScheduledExerciseTasks(exercise, lifecycle, new HashSet<>(List.of(scheduledFuture)), name);
    }

    /**
     * Schedule a set of tasks for the given Exercise for the provided ExerciseLifecycle at the given times.
     *
     * @param exercise  Exercise
     * @param lifecycle ExerciseLifecycle
     * @param tasks     Runnable tasks to be executed at the associated ZonedDateTimes, must be a mutable set
     * @param name      Name of the task
     */
    public void scheduleExerciseTask(Exercise exercise, ExerciseLifecycle lifecycle, Set<Pair<ZonedDateTime, Runnable>> tasks, String name) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled more than once.
        cancelScheduledTaskForLifecycle(exercise.getId(), lifecycle);
        Set<ScheduledFuture<?>> scheduledTasks = exerciseLifecycleService.scheduleMultipleTasks(exercise, lifecycle, tasks);
        addScheduledExerciseTasks(exercise, lifecycle, scheduledTasks, name);
    }

    /**
     * Schedule a task for the given participation for the provided lifecycle.
     *
     * @param participation for which a scheduled action should be created.
     * @param lifecycle     at which the task should be scheduled.
     * @param task          Runnable task to be executed on the lifecycle hook
     * @param name          Name of the task
     */
    public void scheduleParticipationTask(Participation participation, ParticipationLifecycle lifecycle, Runnable task, String name) {
        cancelScheduledTaskForParticipationLifecycle(participation.getExercise().getId(), participation.getId(), lifecycle);
        participationLifecycleService.scheduleTask(participation, lifecycle, task)
                .ifPresent(scheduledTask -> addScheduledParticipationTask(participation, lifecycle, new HashSet<>(List.of(scheduledTask)), name));
    }

    /**
     * Cancel possible schedules tasks for a provided exercise.
     * <p>
     * Additionally, cancels the tasks for participations of that exercise for the corresponding {@link ParticipationLifecycle}.
     *
     * @param exerciseId id of the exercise for which a potentially scheduled task is canceled
     * @param lifecycle  the lifecycle (e.g. release, due date) for which the schedule should be canceled
     */
    public void cancelScheduledTaskForLifecycle(Long exerciseId, ExerciseLifecycle lifecycle) {
        var task = new ExerciseLifecycleKey(exerciseId, lifecycle);
        var taskNames = scheduledExerciseTasks.get(task);
        if (taskNames != null) {
            log.debug("Cancelling scheduled task {} for Exercise (#{}).", lifecycle, exerciseId);
            taskNames.forEach(taskName -> taskName.future().cancel(true));
            removeScheduledExerciseTask(exerciseId, lifecycle);
        }

        ParticipationLifecycle.fromExerciseLifecycle(lifecycle).ifPresent(participationLifecycle -> {
            final Stream<Long> participationIds = scheduledParticipationTasks.keySet().stream().map(ParticipationLifecycleKey::exerciseId)
                    .filter(scheduledExerciseId -> Objects.equals(scheduledExerciseId, exerciseId));
            participationIds.forEach(participationId -> cancelScheduledTaskForParticipationLifecycle(exerciseId, participationId, participationLifecycle));
        });
    }

    /**
     * Cancel possible schedules tasks for a provided participation.
     *
     * @param exerciseId      id of the exercise for which a potentially scheduled task is canceled
     * @param participationId of the participation for which a potential scheduled task is cancelled.
     * @param lifecycle       the lifecycle (e.g. release, due date) for which the schedule should be canceled
     */
    public void cancelScheduledTaskForParticipationLifecycle(Long exerciseId, Long participationId, ParticipationLifecycle lifecycle) {
        var task = new ParticipationLifecycleKey(exerciseId, participationId, lifecycle);
        Set<ScheduledTaskName> taskNames = scheduledParticipationTasks.get(task);
        if (taskNames != null) {
            log.debug("Cancelling scheduled task {} for Participation (#{}).", lifecycle, participationId);
            taskNames.forEach(taskName -> taskName.future().cancel(true));
            removeScheduledParticipationTask(exerciseId, participationId, lifecycle);
        }
    }

    /**
     * Cancels all scheduled tasks for all {@link ParticipationLifecycle ParticipationLifecycles} for the given participation.
     *
     * @param exerciseId      of the exercise the participation belongs to.
     * @param participationId of the participation itself.
     */
    public void cancelAllScheduledParticipationTasks(Long exerciseId, Long participationId) {
        for (final ParticipationLifecycle lifecycle : ParticipationLifecycle.values()) {
            cancelScheduledTaskForParticipationLifecycle(exerciseId, participationId, lifecycle);
        }
    }

    /**
     * Cancels all futures tasks, only use this for testing purposes
     */
    public void clearAllTasks() {
        scheduledParticipationTasks.values().forEach(taskNames -> taskNames.forEach(taskName -> taskName.future().cancel(true)));
        scheduledExerciseTasks.values().forEach(taskNames -> taskNames.forEach(taskName -> taskName.future().cancel(true)));
        scheduledParticipationTasks.clear();
        scheduledExerciseTasks.clear();
    }
}
