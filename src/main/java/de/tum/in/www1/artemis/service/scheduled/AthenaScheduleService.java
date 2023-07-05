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
import de.tum.in.www1.artemis.service.connectors.athena.AthenaService;
import tech.jhipster.config.JHipsterConstants;

@Service
@Profile("athena & scheduling")
public class AthenaScheduleService {

    private final Logger log = LoggerFactory.getLogger(AthenaScheduleService.class);

    private final ExerciseLifecycleService exerciseLifecycleService;

    private final TextExerciseRepository textExerciseRepository;

    private final Environment env;

    private final Map<Long, ScheduledFuture<?>> scheduledAthenaTasks = new HashMap<>();

    private final AthenaService athenaService;

    private final TaskScheduler scheduler;

    public AthenaScheduleService(ExerciseLifecycleService exerciseLifecycleService, TextExerciseRepository textExerciseRepository,
            @Qualifier("taskScheduler") TaskScheduler scheduler, Environment env, AthenaService athenaService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
        this.textExerciseRepository = textExerciseRepository;
        this.scheduler = scheduler;
        this.env = env;
        this.athenaService = athenaService;
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
        runningTextExercises.forEach(this::scheduleExerciseForAthena);
        log.info("Scheduled Athena for {} text exercises with future due dates.", runningTextExercises.size());
    }

    /**
     * Schedule an Athena task for a text exercise with its due date if automatic assessments are enabled and its due date is in the future.
     *
     * @param exercise exercise to schedule Athena for
     */
    public void scheduleExerciseForAthenaIfRequired(TextExercise exercise) {
        if (!exercise.isAutomaticAssessmentEnabled()) {
            cancelScheduledAthena(exercise.getId());
            return;
        }
        // ToDo Needs to be adapted for exam exercises (@Jan Philip Bernius)
        if (exercise.getDueDate() == null || exercise.getDueDate().compareTo(ZonedDateTime.now()) < 0) {
            return;
        }

        scheduleExerciseForAthena(exercise);
    }

    private void scheduleExerciseForAthena(TextExercise exercise) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled for Athena more than once.
        cancelScheduledAthena(exercise.getId());

        final ScheduledFuture<?> future = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, athenaRunnableForExercise(exercise));

        scheduledAthenaTasks.put(exercise.getId(), future);
        log.debug("Scheduled Athena for Text Exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getDueDate());
    }

    /**
     * Schedule an Athena task for a text exercise to start immediately.
     *
     * @param exercise exercise to schedule Athena for
     */
    public void scheduleExerciseForInstantAthena(TextExercise exercise) {
        // TODO: sanity checks.
        scheduler.schedule(athenaRunnableForExercise(exercise), now());
    }

    @NotNull
    private Runnable athenaRunnableForExercise(TextExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            athenaService.submitJob(exercise);
        };
    }

    /**
     * Cancel possible schedules Athena tasks for a provided exercise.
     *
     * @param exerciseId id of the exercise for which a potential Athena task is canceled
     */
    public void cancelScheduledAthena(Long exerciseId) {
        final ScheduledFuture<?> future = scheduledAthenaTasks.get(exerciseId);
        if (future != null) {
            future.cancel(false);
            scheduledAthenaTasks.remove(exerciseId);
        }
    }

    /**
     * Checks if Athena is currently processing submissions of given exercise.
     *
     * @param exercise Exercise to check state
     * @return currently computing Athena?
     */
    public boolean currentlyProcessing(TextExercise exercise) {
        final ScheduledFuture<?> future = scheduledAthenaTasks.get(exercise.getId());
        if (future == null) {
            return false;
        }

        final long delay = future.getDelay(TimeUnit.NANOSECONDS);
        final boolean done = future.isDone();
        final boolean cancelled = future.isCancelled();

        // Check future for scheduled tasks
        // Check athenaService for running tasks
        return !done && !cancelled && delay <= 0 || athenaService.isTaskRunning(exercise.getId());
    }

}
