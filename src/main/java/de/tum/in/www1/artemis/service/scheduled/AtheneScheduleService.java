package de.tum.in.www1.artemis.service.scheduled;

import static java.time.Instant.now;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ExerciseLifecycleService;
import de.tum.in.www1.artemis.service.connectors.athene.AtheneService;
import tech.jhipster.config.JHipsterConstants;

@Service
@Profile("athene & scheduling")
public class AtheneScheduleService {

    private final Logger log = LoggerFactory.getLogger(AtheneScheduleService.class);

    private final ExerciseLifecycleService exerciseLifecycleService;

    private final TextExerciseRepository textExerciseRepository;

    private final Environment env;

    private final Map<Long, ScheduledFuture<?>> scheduledAtheneTasks = new HashMap<>();

    private final AtheneService atheneService;

    private final TaskScheduler scheduler;

    public AtheneScheduleService(ExerciseLifecycleService exerciseLifecycleService, TextExerciseRepository textExerciseRepository,
            @Qualifier("taskScheduler") TaskScheduler scheduler, Environment env, AtheneService atheneService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
        this.textExerciseRepository = textExerciseRepository;
        this.scheduler = scheduler;
        this.env = env;
        this.atheneService = atheneService;
    }

    @PostConstruct
    private void scheduleRunningExercisesOnStartup() {
        final Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
            // only execute this on production server, i.e. when the prod profile is active
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }
        final List<TextExercise> runningTextExercises = textExerciseRepository.findAllAutomaticAssessmentTextExercisesWithFutureDueDate();
        runningTextExercises.forEach(this::scheduleExerciseForAthene);
        log.info("Scheduled Athene for {} text exercises with future due dates.", runningTextExercises.size());
    }

    /**
     * Schedule an Athene task for a text exercise with its due date if automatic assessments are enabled and its due date is in the future.
     * @param exercise exercise to schedule Athene for
     */
    public void scheduleExerciseForAtheneIfRequired(TextExercise exercise) {
        if (!exercise.isAutomaticAssessmentEnabled()) {
            cancelScheduledAthene(exercise.getId());
            return;
        }
        // ToDo Needs to be adapted for exam exercises (@Jan Philip Bernius)
        if (exercise.getDueDate() == null || exercise.getDueDate().compareTo(ZonedDateTime.now()) < 0) {
            return;
        }

        scheduleExerciseForAthene(exercise);
    }

    private void scheduleExerciseForAthene(TextExercise exercise) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled for Athene more than once.
        cancelScheduledAthene(exercise.getId());

        final ScheduledFuture<?> future = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, atheneRunnableForExercise(exercise));

        scheduledAtheneTasks.put(exercise.getId(), future);
        log.debug("Scheduled Athene for Text Exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getDueDate());
    }

    /**
     * Schedule an Athene task for a text exercise to start immediately.
     * @param exercise exercise to schedule Athene for
     */
    public void scheduleExerciseForInstantAthene(TextExercise exercise) {
        // TODO: sanity checks.
        scheduler.schedule(atheneRunnableForExercise(exercise), now());
    }

    @NotNull
    private Runnable atheneRunnableForExercise(TextExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            atheneService.submitJob(exercise);
        };
    }

    /**
     * Cancel possible schedules Athene tasks for a provided exercise.
     * @param exerciseId id of the exercise for which a potential Athene task is canceled
     */
    public void cancelScheduledAthene(Long exerciseId) {
        final ScheduledFuture<?> future = scheduledAtheneTasks.get(exerciseId);
        if (future != null) {
            future.cancel(false);
            scheduledAtheneTasks.remove(exerciseId);
        }
    }

    /**
     * Checks if Athene is currently processing submissions of given exercise.
     * @param exercise Exercise to check state
     * @return currently computing Athene?
     */
    public boolean currentlyProcessing(TextExercise exercise) {
        final ScheduledFuture<?> future = scheduledAtheneTasks.get(exercise.getId());
        if (future == null) {
            return false;
        }

        final long delay = future.getDelay(TimeUnit.NANOSECONDS);
        final boolean done = future.isDone();
        final boolean cancelled = future.isCancelled();

        // Check future for scheduled tasks
        // Check atheneService for running tasks
        return !done && !cancelled && delay <= 0 || atheneService.isTaskRunning(exercise.getId());
    }

}
