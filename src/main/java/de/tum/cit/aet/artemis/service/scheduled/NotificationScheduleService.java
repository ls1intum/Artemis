package de.tum.cit.aet.artemis.service.scheduled;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.StartupDelayConfig.NOTIFICATION_SCHEDULE_DELAY_SEC;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseLifecycle;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.service.ExerciseDateService;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.service.notifications.SingleUserNotificationService;

@Service
@Profile(PROFILE_SCHEDULING)
public class NotificationScheduleService {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduleService.class);

    private final ScheduleService scheduleService;

    private final ExerciseRepository exerciseRepository;

    private final ProfileService profileService;

    private final GroupNotificationService groupNotificationService;

    private final SingleUserNotificationService singleUserNotificationService;

    private final TaskScheduler scheduler;

    public NotificationScheduleService(ScheduleService scheduleService, ExerciseRepository exerciseRepository, ProfileService profileService,
            GroupNotificationService groupNotificationService, SingleUserNotificationService singleUserNotificationService, @Qualifier("taskScheduler") TaskScheduler scheduler) {
        this.scheduleService = scheduleService;
        this.exerciseRepository = exerciseRepository;
        this.profileService = profileService;
        this.groupNotificationService = groupNotificationService;
        this.singleUserNotificationService = singleUserNotificationService;
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // schedule the task after the application has started to avoid delaying the start of the application
        scheduler.schedule(this::scheduleRunningNotificationProcessesOnStartup, Instant.now().plusSeconds(NOTIFICATION_SCHEDULE_DELAY_SEC));
    }

    /**
     * Schedules ongoing notification processes shortly after server start up
     */
    private void scheduleRunningNotificationProcessesOnStartup() {
        try {
            if (profileService.isDevActive()) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }

            checkSecurityUtils();

            // EXERCISE_RELEASED
            Set<Exercise> exercisesToBeScheduledForReleaseDate = exerciseRepository.findAllExercisesWithCurrentOrUpcomingReleaseDate(ZonedDateTime.now());
            exercisesToBeScheduledForReleaseDate.forEach(this::scheduleNotificationForReleasedExercise);
            log.info("Scheduled {} notifications for released exercises.", exercisesToBeScheduledForReleaseDate.size());

            // EXERCISE_SUBMISSION_ASSESSED
            Set<Exercise> exercisesToBeScheduledForAssessmentDueDate = exerciseRepository.findAllExercisesWithCurrentOrUpcomingAssessmentDueDate(ZonedDateTime.now());
            exercisesToBeScheduledForAssessmentDueDate.forEach(this::scheduleNotificationForAssessedExercisesSubmissions);
            log.info("Scheduled {} notifications for assessed exercise submissions.", exercisesToBeScheduledForAssessmentDueDate.size());
        }
        catch (Exception exception) {
            log.error("Failed to start NotificationScheduleService", exception);
        }
    }

    /// EXERCISE_RELEASED

    /**
     * updateScheduling method for the notificationType EXERCISE_RELEASED
     *
     * @param exercise that should trigger a notification when it is released
     */
    public void updateSchedulingForReleasedExercises(Exercise exercise) {
        if (exercise.getReleaseDate() == null || ZonedDateTime.now().isAfter(exercise.getReleaseDate())) {
            // to avoid canceling more important tasks we simply return here.
            // to make sure no wrong notification is sent out the date is checked again in the concrete notification method
            return;
        }
        if (exercise.isCourseExercise()) {
            scheduleNotificationForReleasedExercise(exercise);
        }
    }

    /**
     * The place where the actual tasks/methods are called/scheduled that should be run at the exercise release time
     *
     * @param exercise which will be announced by a notifications at release date
     */
    private void scheduleNotificationForReleasedExercise(Exercise exercise) {
        try {
            checkSecurityUtils();
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, () -> {
                checkSecurityUtils();
                Exercise foundCurrentVersionOfScheduledExercise = exerciseRepository.findByIdElseThrow(exercise.getId());
                if (checkIfTimeIsCorrectForScheduledTask(foundCurrentVersionOfScheduledExercise.getReleaseDate())) {
                    groupNotificationService.notifyAllGroupsAboutReleasedExercise(foundCurrentVersionOfScheduledExercise);
                }
            });
            log.debug("Scheduled notify about started exercise after due date for exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getReleaseDate());
        }
        catch (Exception exception) {
            log.error("Failed to schedule notification for exercise {}", exercise.getId(), exception);
        }
    }

    /// EXERCISE_SUBMISSION_ASSESSED

    /**
     * updateScheduling method for the notificationType EXERCISE_SUBMISSION_ASSESSED
     *
     * @param exercise that should trigger a notification when the assessment due date is over
     */
    public void updateSchedulingForAssessedExercisesSubmissions(Exercise exercise) {
        checkSecurityUtils();
        if (ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            // to make sure no wrong notification is sent out the date is checked again in the concrete notification method
            scheduleService.cancelScheduledTaskForLifecycle(exercise.getId(), ExerciseLifecycle.ASSESSMENT_DUE);
            return;
        }
        if (exercise.isCourseExercise()) {
            scheduleNotificationForAssessedExercisesSubmissions(exercise);
        }
    }

    /**
     * The place where the actual tasks/methods are called/scheduled that should be run when at the assessment due date of the exercise
     *
     * @param exercise which will be announced by a notifications at release date
     */
    private void scheduleNotificationForAssessedExercisesSubmissions(Exercise exercise) {
        try {
            checkSecurityUtils();
            scheduleService.scheduleTask(exercise, ExerciseLifecycle.ASSESSMENT_DUE, () -> {
                checkSecurityUtils();
                Exercise foundCurrentVersionOfScheduledExercise = exerciseRepository.findByIdElseThrow(exercise.getId());
                if (checkIfTimeIsCorrectForScheduledTask(foundCurrentVersionOfScheduledExercise.getAssessmentDueDate())) {
                    singleUserNotificationService.notifyUsersAboutAssessedExerciseSubmission(foundCurrentVersionOfScheduledExercise);
                }
            });
            log.debug("Scheduled notify about assessed exercise submission after assessment due date for exercise '{}' (#{}) at {}.", exercise.getTitle(), exercise.getId(),
                    exercise.getAssessmentDueDate());
        }
        catch (Exception exception) {
            log.error("Failed to schedule notification for exercise {}", exercise.getId(), exception);
        }
    }

    /// SHARED AUXILIARY

    /**
     * Check if relevant time for scheduled notification process is (still) valid at the expected execution time
     *
     * @param relevantTime of the scheduled event (e.g. release Date for scheduled released exercise notifications)
     * @return true if the time is valid else false
     */
    private boolean checkIfTimeIsCorrectForScheduledTask(ZonedDateTime relevantTime) {
        // Only send a notification if relevantTime is defined and close to the current time (i.e. in the range [now-2 minutes, now+2 minutes]) (due to possible delays in
        // scheduling)
        return relevantTime != null && !relevantTime.isBefore(ZonedDateTime.now().minusMinutes(2)) && !relevantTime.isAfter(ZonedDateTime.now().plusMinutes(2));
    }

    /**
     * Checks the SecurityUtils for authentication, if not yet authenticated do so.
     */
    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }
}
