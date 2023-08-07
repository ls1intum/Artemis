package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ExerciseLifecycleService;
import de.tum.in.www1.artemis.service.ProfileService;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaSubmissionSendingService;

@Service
@Profile("athena & scheduling")
public class AthenaScheduleService {

    private final Logger log = LoggerFactory.getLogger(AthenaScheduleService.class);

    private final ExerciseLifecycleService exerciseLifecycleService;

    private final TextExerciseRepository textExerciseRepository;

    private final ProfileService profileService;

    private final Map<Long, ScheduledFuture<?>> scheduledAthenaTasks = new HashMap<>();

    private final AthenaSubmissionSendingService athenaSubmissionSendingService;

    public AthenaScheduleService(ExerciseLifecycleService exerciseLifecycleService, TextExerciseRepository textExerciseRepository, ProfileService profileService,
            AthenaSubmissionSendingService athenaSubmissionSendingService) {
        this.exerciseLifecycleService = exerciseLifecycleService;
        this.textExerciseRepository = textExerciseRepository;
        this.profileService = profileService;
        this.athenaSubmissionSendingService = athenaSubmissionSendingService;
    }

    /**
     * Schedule Athena tasks for all text exercises with future due dates on startup.
     */
    @PostConstruct
    public void scheduleRunningExercisesOnStartup() {
        if (profileService.isDev()) {
            // only execute this on production server, i.e. when the prod profile is active
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }
        final List<TextExercise> runningTextExercises = textExerciseRepository.findAllAutomaticAssessmentTextExercisesWithFutureDueDate();
        runningTextExercises.forEach(this::scheduleExerciseForAthenaIfRequired);
        log.info("Scheduled Athena for {} text exercises with future due dates.", runningTextExercises.size());
    }

    /**
     * Schedule an Athena task for a text exercise with its due date if automatic assessments are enabled and its due date is in the future.
     *
     * @param exercise exercise to schedule Athena for
     */
    public void scheduleExerciseForAthenaIfRequired(TextExercise exercise) {
        if (!exercise.isFeedbackSuggestionsEnabled()) {
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

    @NotNull
    private Runnable athenaRunnableForExercise(TextExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            athenaSubmissionSendingService.sendSubmissions(exercise);
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

}
