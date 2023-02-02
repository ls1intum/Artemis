package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationLifecycle;
import de.tum.in.www1.artemis.domain.participation.Participation;

@Service
public class ParticipationLifecycleService {

    private final Logger log = LoggerFactory.getLogger(ParticipationLifecycleService.class);

    private final TaskScheduler scheduler;

    public ParticipationLifecycleService(@Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sets up a scheduled {@link Runnable} task in the lifecycle of a {@link Participation}.
     *
     * Tasks are performed in a background thread managed by a {@code TaskScheduler}.
     * See {@code TaskSchedulingConfiguration}.
     * <p>
     * <b>Important:</b>
     * Scheduled tasks are not persisted across application restarts.
     * Therefore, schedule your events from both your application logic (e.g. exercise modification) and on application startup.
     * You can use the {@code PostConstruct} annotation to call one service method on startup.
     *
     * @param participation for which a task should be scheduled.
     * @param lifecycle at which the task should be scheduled.
     * @param task the action that should be performed at the date of the given lifecycle.
     * @return a scheduled task that performs the given action at the appropriate time.
     *         Returns nothing if the given participation does not have date at which the given lifecycle task could be run.
     *         E.g. {@link ParticipationLifecycle#BUILD_AND_TEST_AFTER_DUE_DATE} for non-programming exercises will return nothing.
     */
    public Optional<ScheduledFuture<?>> scheduleTask(Participation participation, ParticipationLifecycle lifecycle, Runnable task) {
        final Optional<ZonedDateTime> lifecycleDate = getDateForLifecycle(participation, lifecycle);
        if (lifecycleDate.isPresent()) {
            final ScheduledFuture<?> future = scheduler.schedule(task, lifecycleDate.get().toInstant());
            log.debug("Scheduled task for participation {} in exercise '{}' ({}) to trigger on {}.", participation.getId(), participation.getExercise().getTitle(),
                    participation.getExercise().getId(), lifecycle);
            return Optional.of(future);
        }
        else {
            log.warn("Cannot schedule a task for lifecycle {} for participation (id: {}, exercise: {}, exercise id: {}) as no appropriate date is known!", lifecycle,
                    participation.getId(), participation.getExercise().getTitle(), participation.getExercise().getId());
            return Optional.empty();
        }
    }

    private Optional<ZonedDateTime> getDateForLifecycle(Participation participation, ParticipationLifecycle lifecycle) {
        return switch (lifecycle) {
            case DUE -> ExerciseDateService.getDueDate(participation);
            case BUILD_AND_TEST_AFTER_DUE_DATE -> getBuildAndTestAfterDueDate(participation);
        };
    }

    /**
     * Finds the time when the build and test after due date task should be run.
     *
     * If the participation has an individual due date, this date may be after the regular build and test date for the exercise.
     * Therefore, always the latest possible of the two options has to be chosen.
     *
     * @param participation for which the correct date for the build and test after due date lifecycle should be determined.
     * @return nothing, if the exercise has no build and test after due date.
     *         Otherwise, returns the latest time between individual due date or build and test date.
     */
    private Optional<ZonedDateTime> getBuildAndTestAfterDueDate(Participation participation) {
        if (participation.getExercise() instanceof ProgrammingExercise programmingExercise && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null) {
            final ZonedDateTime exerciseBuildAndTestAfterDueDate = programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate();
            final Optional<ZonedDateTime> dueDate = ExerciseDateService.getDueDate(participation);

            if (dueDate.map(date -> date.isAfter(exerciseBuildAndTestAfterDueDate)).orElse(false)) {
                return dueDate;
            }
            else {
                return Optional.of(exerciseBuildAndTestAfterDueDate);
            }
        }
        else {
            return Optional.empty();
        }
    }
}
