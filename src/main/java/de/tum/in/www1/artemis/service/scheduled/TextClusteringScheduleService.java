package de.tum.in.www1.artemis.service.scheduled;

import static java.time.Instant.now;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

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
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ExerciseLifecycleService;
import de.tum.in.www1.artemis.service.TextClusteringService;
import de.tum.in.www1.artemis.service.TextExerciseService;
import io.github.jhipster.config.JHipsterConstants;

@Service
@Profile("automaticText")
public class TextClusteringScheduleService {

    private final Logger log = LoggerFactory.getLogger(TextClusteringScheduleService.class);

    private final ExerciseLifecycleService exerciseLifecycleService;

    private final TextExerciseService textExerciseService;

    private final Environment env;

    private final Map<Long, ScheduledFuture> scheduledClusteringTasks = new HashMap<>();

    private final TextClusteringService textClusteringService;

    private final TaskScheduler scheduler;

    public TextClusteringScheduleService(ExerciseLifecycleService exerciseLifecycleService, TextExerciseService textExerciseService, TextClusteringService textClusteringService,
            @Qualifier("taskScheduler") TaskScheduler scheduler, Environment env) {
        this.exerciseLifecycleService = exerciseLifecycleService;
        this.textExerciseService = textExerciseService;
        this.textClusteringService = textClusteringService;
        this.scheduler = scheduler;
        this.env = env;
    }

    @PostConstruct
    private void scheduleRunningExercisesOnStartup() {
        Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
            // only execute this on production server, i.e. when the prod profile is active
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }
        List<TextExercise> runningTextExercises = textExerciseService.findAllAutomaticAssessmentTextExercisesWithFutureDueDate();
        runningTextExercises.forEach(this::scheduleExerciseForClustering);
        log.info("Scheduled text clustering for " + runningTextExercises.size() + " text exercises with future due dates.");
    }

    /**
     * Schedule a clustering task for a text exercise with its due date if automatic assessments are enabled and its due date is in the future.
     * @param exercise exercise to schedule clustering for
     */
    public void scheduleExerciseForClusteringIfRequired(TextExercise exercise) {
        if (!exercise.isAutomaticAssessmentEnabled()) {
            cancelScheduledClustering(exercise);
            return;
        }
        if (exercise.getDueDate() == null || exercise.getDueDate().compareTo(ZonedDateTime.now()) < 0) {
            return;
        }

        scheduleExerciseForClustering(exercise);
    }

    private void scheduleExerciseForClustering(TextExercise exercise) {
        // check if already scheduled for exercise. if so, cancel.
        // no exercise should be scheduled for clustering more than once.
        cancelScheduledClustering(exercise);

        ScheduledFuture future = exerciseLifecycleService.scheduleTask(exercise, ExerciseLifecycle.DUE, clusteringRunnableForExercise(exercise));

        scheduledClusteringTasks.put(exercise.getId(), future);
        log.debug("Scheduled Clustering for Text Exercise \"" + exercise.getTitle() + "\" (#" + exercise.getId() + ") for " + exercise.getDueDate() + ".");
    }

    /**
     * Schedule a clustering task for a text exercise to start immediately.
     * @param exercise exercise to schedule clustering for
     */
    public void scheduleExerciseForInstantClustering(TextExercise exercise) {
        // TODO: sanity checks.
        scheduler.schedule(clusteringRunnableForExercise(exercise), now());
    }

    @NotNull
    private Runnable clusteringRunnableForExercise(TextExercise exercise) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            textClusteringService.calculateClusters(exercise);
        };
    }

    /**
     * Cancel possible schedules clustering tasks for a provided exercise.
     * @param exercise exercise for which a potential clustering task is canceled
     */
    public void cancelScheduledClustering(TextExercise exercise) {
        ScheduledFuture future = scheduledClusteringTasks.get(exercise.getId());
        if (future != null) {
            future.cancel(false);
            scheduledClusteringTasks.remove(exercise.getId());
        }
    }

}
