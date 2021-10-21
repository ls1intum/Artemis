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
import tech.jhipster.config.JHipsterConstants;

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
        if (exercise.getReleaseDate() == null || ZonedDateTime.now().isAfter(exercise.getReleaseDate())) {
            // to avoid canceling more important tasks we simply return here.
            // to make sure no wrong notification is sent out the date is checked again in the concrete notification method
            return;
        }
        if (exercise.isCourseExercise()) {
            scheduleNotificationForExercise(exercise);
        }
    }

    /**
     * The place where the actual tasks/methods are called/scheduled that should be run at the exercise release time
     * @param exercise which will be announced by a notifications at release date
     */
    private void scheduleNotificationForExercise(Exercise exercise) {
        try {
            if (!SecurityUtils.isAuthenticated()) {
                SecurityUtils.setAuthorizationObject();
            }
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, () -> {
                if (!SecurityUtils.isAuthenticated()) {
                    SecurityUtils.setAuthorizationObject();
                }
                // only send a notification if ReleaseDate is null or not in the future (i.e. in the range [now-2 minutes, now]) (due to possible delays in scheduling)
                if (exercise.getReleaseDate() == null
                        || !exercise.getReleaseDate().isBefore(ZonedDateTime.now().minusMinutes(2)) && !exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
                    groupNotificationService.notifyAllGroupsAboutReleasedExercise(exercise);
                }
            });
            log.debug("Scheduled notify about started exercise after due date for exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getReleaseDate());
        }
        catch (Exception exception) {
            log.error("Failed to schedule notification for exercise " + exercise.getId(), exception);
        }
    }
}
