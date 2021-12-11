package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY_WEEKLY_SUMMARY_BASIC;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.util.Tuple;
import tech.jhipster.config.JHipsterConstants;

@Service
@Profile("scheduling")
public class WeeklySummaryService {

    private final Logger log = LoggerFactory.getLogger(NotificationScheduleService.class);

    private final ScheduleService scheduleService;

    private final Environment environment;

    private final MailService mailService;

    private final UserRepository userRepository;

    private final NotificationSettingRepository notificationSettingRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final Map<Tuple<Long, ZonedDateTime>, Set<ScheduledFuture<?>>> scheduledTasks = new HashMap<>();

    public WeeklySummaryService(ScheduleService scheduleService, Environment environment, MailService mailService, UserRepository userRepository,
            NotificationSettingRepository notificationSettingRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository) {
        this.scheduleService = scheduleService;
        this.environment = environment;
        this.mailService = mailService;
        this.userRepository = userRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Prepare weekly summaries scheduling after server start up
     */
    @PostConstruct
    public void scheduleWeeklySummariesOnStartUp() {
        try {
            Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
            if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }
            SecurityUtils.setAuthorizationObject();

            scheduleWeeklySummaries();

            log.info("Scheduled weekly summaries on start up.");
        }
        catch (Exception exception) {
            log.error("Failed to start NotificationScheduleService", exception);
        }
    }

    /**
     * Begin the process of weekly summaries
     * i.e. find all active Artemis users that have weekly summaries enabled in their notification settings
     * and initiate the creation of weekly summary email for each found user.
     */
    @Async
    void scheduleWeeklySummaries() {
        /*
         * try { checkSecurityUtils(); scheduleService.scheduleTask(exercise, ExerciseLifecycle.RELEASE, () -> { checkSecurityUtils(); Exercise
         * foundCurrentVersionOfScheduledExercise; // if the exercise has been updated in the meantime the scheduled immutable exercise is outdated and has to be replaced by the
         * current one in the DB try { foundCurrentVersionOfScheduledExercise = exerciseRepository.findByIdElseThrow(exercise.getId()); } catch (EntityNotFoundException
         * entityNotFoundException) { log.debug("Scheduled notification is no longer in the database " + exercise.getId()); return; } // only send a notification if ReleaseDate is
         * defined and not in the future (i.e. in the range [now-2 minutes, now]) (due to possible delays in scheduling) if (foundCurrentVersionOfScheduledExercise.getReleaseDate()
         * != null && !foundCurrentVersionOfScheduledExercise.getReleaseDate().isBefore(ZonedDateTime.now().minusMinutes(2)) &&
         * !foundCurrentVersionOfScheduledExercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
         * groupNotificationService.notifyAllGroupsAboutReleasedExercise(foundCurrentVersionOfScheduledExercise); } });
         * log.debug("Scheduled notify about started exercise after due date for exercise '{}' (#{}) for {}.", exercise.getTitle(), exercise.getId(), exercise.getReleaseDate()); }
         * catch (Exception exception) { log.error("Failed to schedule notification for exercise " + exercise.getId(), exception); } } // find all Artemis users // Could be
         * improved by getting only still active users Set<User> allUsers = userRepository.getAll(); // filter out users that do not want to receive weekly summaries Set<User>
         * filteredUsers = allUsers.stream().filter(this::checkIfWeeklySummaryIsAllowedByNotificationSettingsForGivenUser).collect(Collectors.toSet()); if(!allUsers.isEmpty()) {
         * filteredUsers.forEach(this::prepareWeeklySummaryForUser); }
         */
    }

    /**
     * Checks and sets the needed authentication
     */
    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }

    /**
     * Check if this user should receive weekly summary emails
     * @param user who is checked
     * @return true if user has weekly summaries enabled else false
     */
    private boolean checkIfWeeklySummaryIsAllowedByNotificationSettingsForGivenUser(User user) {
        Set<NotificationSetting> notificationSettings = notificationSettingRepository.findAllNotificationSettingsForRecipientWithId(user.getId());
        NotificationSetting weeklySummarySetting = notificationSettings.stream().filter(setting -> setting.getSettingId().equals(NOTIFICATION__WEEKLY_SUMMARY_WEEKLY_SUMMARY_BASIC))
                .findFirst().orElse(null);
        if (weeklySummarySetting == null)
            return false;
        return weeklySummarySetting.isEmail();
    }

    /**
     * Prepares all needed information to create the weekly summary email for one user
     * and calls the MailService
     * @param user for whom the weekly summary email should be prepared
     */
    private void prepareWeeklySummaryForUser(User user) {
        // Find courses the user is part of
        /*
         * Set<Course> courses = courseRepository. // Get released exercises of the last week (which are not yet over, i.e. working due date is not passed yet) Set<Exercise>
         * newExercisesOfThisWeek = exerciseRepository.
         */
    }
}
