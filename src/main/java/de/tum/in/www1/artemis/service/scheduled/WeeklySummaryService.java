package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsService;

@Service
@Profile("scheduling")
public class WeeklySummaryService {

    private final Logger log = LoggerFactory.getLogger(NotificationScheduleService.class);

    private final Environment environment;

    private final MailService mailService;

    private final UserRepository userRepository;

    private final NotificationSettingRepository notificationSettingRepository;

    private final CourseService courseService;

    private final TaskScheduler scheduler;

    private final NotificationSettingsService notificationSettingsService;

    private ZonedDateTime oneWeekAgo;

    public WeeklySummaryService(Environment environment, MailService mailService, UserRepository userRepository, NotificationSettingRepository notificationSettingRepository,
            CourseService courseService, @Qualifier("taskScheduler") TaskScheduler scheduler, NotificationSettingsService notificationSettingsService) {
        this.environment = environment;
        this.mailService = mailService;
        this.userRepository = userRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.courseService = courseService;
        this.scheduler = scheduler;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Prepare weekly summaries scheduling after server start up
     */
    @PostConstruct
    public void scheduleWeeklySummariesOnStartUp() {
        try {
            Collection<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
            /*
             * TODO uncomment after testing! if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) { // only execute this on production server, i.e. when the
             * prod profile is active // NOTE: if you want to test this locally, please comment it out, but do not commit the changes return; }
             * SecurityUtils.setAuthorizationObject();
             */

            // i.e. next Friday at 17:00
            LocalDateTime nextWeeklySummaryDate = ZonedDateTime.now().toLocalDateTime().with(DayOfWeek.FRIDAY).withHour(17).withMinute(0);
            // For local testing :
            nextWeeklySummaryDate = ZonedDateTime.now().toLocalDateTime(); // TODO comment in

            ZoneOffset zoneOffset = ZonedDateTime.now().getZone().getRules().getOffset(Instant.now());

            scheduler.scheduleAtFixedRate(scheduleWeeklySummaries(), nextWeeklySummaryDate.toInstant(zoneOffset), Duration.ofDays(7));

            log.info("Scheduled weekly summaries on start up.");
        }
        catch (Exception exception) {
            log.error("Failed to start WeeklySummaryService", exception);
        }
    }

    /**
     * Begin the process of weekly summaries
     * i.e. find all active Artemis users that have weekly summaries enabled in their notification settings
     * and initiate the creation of weekly summary email for each found user.
     */
    @Async
    Runnable scheduleWeeklySummaries() {
        return () -> {
            checkSecurityUtils();
            oneWeekAgo = ZonedDateTime.now().minusWeeks(1);
            // find all Artemis users // Could be improved by getting only still active users
            Set<User> allUsers = new HashSet<>(userRepository.findAllWithGroupsAndAuthorities());
            // filter out users that do not want to receive weekly summaries
            Set<User> filteredUsers = allUsers.stream().filter(this::checkIfWeeklySummaryIsAllowedByNotificationSettingsForGivenUser).collect(Collectors.toSet());
            if (!filteredUsers.isEmpty()) {
                filteredUsers.forEach(this::prepareWeeklySummaryForUser);
            }
        };
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
        notificationSettings = notificationSettingsService.checkLoadedNotificationSettingsForCorrectness(notificationSettings);
        NotificationSetting weeklySummarySetting = notificationSettings.stream()
                .filter(setting -> setting.getSettingId().equals(NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY)).findFirst().orElse(null);
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
        checkSecurityUtils();

        // Get all courses with exercises, lectures and exams (filtered for given user)
        List<Course> courses = courseService.findAllActiveWithExercisesAndLecturesAndExamsForUser(user);

        // Get released exercises of the last week (which are not yet over, i.e. working due date is not passed yet)
        Set<Exercise> newExercisesOfThisWeek = getAllExercisesOfThisWeek(courses);

        // More elements that should be displayed in weekly summaries can be extracted here
        // Currently only exercises are used for weekly summaries

        mailService.sendWeeklySummaryEmail(user, newExercisesOfThisWeek);
    }

    /**
     * @param courses which exercises will be extracted
     * @return all still active exercises of this week based on the students courses
     */
    private Set<Exercise> getAllExercisesOfThisWeek(List<Course> courses) {
        Set<Exercise> newExercisesOfThisWeek = new HashSet<>();
        courses.forEach(course -> newExercisesOfThisWeek
                .addAll(course.getExercises().stream().filter(exercise -> shouldExerciseBePartOfWeeklySummary(exercise)).collect(Collectors.toSet())));
        return newExercisesOfThisWeek;
    }

    /**
     * Checks if an exercise should be part of the weekly summary
     * Exercise should have been released, not yet ended, and the release should have been in the last 7 days
     *
     * @param exercise that should be checked
     * @return true if the exercise should be part of the weekly summary else false
     */
    private boolean shouldExerciseBePartOfWeeklySummary(Exercise exercise) {
        if (exercise == null || exercise.getReleaseDate() == null)
            return false;
        return exercise.isReleased() && exercise.getReleaseDate().isAfter(oneWeekAgo) && !exercise.isEnded();
    }
}
