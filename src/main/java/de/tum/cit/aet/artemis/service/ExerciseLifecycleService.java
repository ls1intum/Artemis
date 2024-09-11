package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.util.Tuple;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

@Profile(PROFILE_CORE)
@Service
public class ExerciseLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseLifecycleService.class);

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
     * @param exercise      The exercise for which the task is scheduled
     * @param lifecycleDate The date at which the task should be executed
     * @param lifecycle     The lifecycle event that triggers the task
     * @param task          The task to be executed
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task or check whether it has been executed.
     */
    public ScheduledFuture<?> scheduleTask(Exercise exercise, ZonedDateTime lifecycleDate, ExerciseLifecycle lifecycle, Runnable task) {
        final ScheduledFuture<?> future = scheduler.schedule(task, lifecycleDate.toInstant());
        log.debug("Scheduled Task for Exercise \"{}\" (#{}) to trigger on {}.", exercise.getTitle(), exercise.getId(), lifecycle);
        return future;
    }

    /**
     * Allow to schedule a {@code Runnable} task in the lifecycle of an exercise. ({@code ExerciseLifecycle}) Tasks are performed in a background thread managed by a
     * {@code TaskScheduler}. See {@code TaskSchedulingConfiguration}. <b>Important:</b> Scheduled tasks are not persisted across application restarts. Therefore, schedule your
     * events from both your application logic (e.g. exercise modification) and on application startup. You can use the {@code PostConstruct} Annotation to call one service method
     * on startup.
     *
     * @param exercise  The exercise for which the task is scheduled
     * @param lifecycle The lifecycle event that triggers the task
     * @param task      The task to be executed
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task or check whether it has been executed.
     */
    public ScheduledFuture<?> scheduleTask(Exercise exercise, ExerciseLifecycle lifecycle, Runnable task) {
        return scheduleTask(exercise, lifecycle.getDateFromExercise(exercise), lifecycle, task);
    }

    /**
     * Allow to schedule a {@code Runnable} task in the lifecycle of a quiz exercise. ({@code ExerciseLifecycle}) Tasks are performed in a background thread managed by a
     * {@code TaskScheduler}. See {@code TaskSchedulingConfiguration}. <b>Important:</b> Scheduled tasks are not persisted across application restarts. Therefore, schedule your
     * events from both your application logic (e.g. exercise modification) and on application startup. You can use the {@code PostConstruct} Annotation to call one service method
     * on startup.
     *
     * @param exercise  The quiz exercise for which the task is scheduled
     * @param batch     The quiz batch for which the task is scheduled
     * @param lifecycle The lifecycle event that triggers the task
     * @param task      The task to be executed
     * @return The {@code ScheduledFuture<?>} allows to later cancel the task or check whether it has been executed.
     */
    public ScheduledFuture<?> scheduleTask(QuizExercise exercise, QuizBatch batch, ExerciseLifecycle lifecycle, Runnable task) {
        if (exercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
            return scheduleTask(exercise, lifecycle.getDateFromQuizBatch(batch, exercise), lifecycle, task);
        }
        else {
            return scheduleTask(exercise, lifecycle.getDateFromExercise(exercise), lifecycle, task);
        }
    }

    /**
     * Allow scheduling multiple {@code Runnable} tasks in the lifecycle of an exercise at distinct points in time. ({@code ExerciseLifecycle}) Tasks are performed in a
     * background thread managed by a {@code TaskScheduler}. See {@code TaskSchedulingConfiguration}. <b>Important:</b> Scheduled tasks are not persisted across application
     * restarts. Therefore, schedule your events from both your application logic (e.g. exercise modification) and on application startup. You can use the {@code PostConstruct}
     * Annotation to call one service method on startup.
     *
     * @param exercise  The exercise for which the tasks are scheduled
     * @param lifecycle The lifecycle event that triggers the tasks
     * @param tasks     The tasks to be executed at distinct points in time
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
