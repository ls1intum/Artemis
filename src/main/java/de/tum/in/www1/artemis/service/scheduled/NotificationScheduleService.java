package de.tum.in.www1.artemis.service.scheduled;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import io.github.jhipster.config.JHipsterConstants;

@Service
@Profile("scheduling")
public class NotificationScheduleService implements IExerciseScheduleService<Exercise> {

    private final Logger log = LoggerFactory.getLogger(NotificationScheduleService.class);

    private final ScheduleService scheduleService;

    private final ExerciseRepository exerciseRepository;

    private final Environment environment;

    private final GroupNotificationService groupNotificationService;

    public NotificationScheduleService(ScheduleService scheduleService, ExerciseRepository exerciseRepository, GroupNotificationService groupNotificationService,
            Environment environment) {
        this.scheduleService = scheduleService;
        this.exerciseRepository = exerciseRepository;
        this.environment = environment;
        this.groupNotificationService = groupNotificationService;
    }

    @PostConstruct
    @Override
    public void scheduleRunningExercisesOnStartup() {
        try {
            Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
            if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }
            SecurityUtils.setAuthorizationObject();

            Set<Exercise> exercisesToBeScheduled = exerciseRepository.findAllExercisesWithCurrentOrUpcomingReleaseDate(ZonedDateTime.now());
            exercisesToBeScheduled.forEach(this::scheduleNotificationForExercise);

            log.info("Scheduled {} exercise notifications.", exercisesToBeScheduled.size());
        }
        catch (Exception exception) {
            log.error("Failed to start NotificationScheduleService", exception);
        }
    }

    @Override
    public void updateScheduling(Exercise exercise) {
        if (ZonedDateTime.now().isAfter(exercise.getReleaseDate())) {
            // TODO NEED HELP Not sure if this might break more important tasks
            scheduleService.cancelScheduledTaskForLifecycle(exercise.getId(), ExerciseLifecycle.RELEASE);
            return;
        }
        if (exercise.isCourseExercise()) {
            scheduleNotificationForExercise(exercise);
        }
    }

    public void scheduleNotificationForExercise(Exercise exercise) {
        try {
            if (!SecurityUtils.isAuthenticated()) {
                SecurityUtils.setAuthorizationObject();
            }
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, () -> {
                groupNotificationService.notifyStudentAndTutorGroupAboutStartedExercise(exercise);
            });
            log.debug("Scheduled notify about started exercise after due date for exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getReleaseDate());
        }
        catch (Exception exception) {
            log.error("Failed to schedule notification for exercise " + exercise.getId(), exception);
        }
    }
}
