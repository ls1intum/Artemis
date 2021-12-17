package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsService;

@Service
public class EmailSummaryService {

    private final MailService mailService;

    private final UserRepository userRepository;

    private final NotificationSettingRepository notificationSettingRepository;

    private final CourseService courseService;

    private final NotificationSettingsService notificationSettingsService;

    private Duration scheduleInterval;

    private final Duration weekly = Duration.ofDays(7);

    public EmailSummaryService(MailService mailService, UserRepository userRepository, NotificationSettingRepository notificationSettingRepository, CourseService courseService,
            NotificationSettingsService notificationSettingsService) {
        this.mailService = mailService;
        this.userRepository = userRepository;
        this.notificationSettingRepository = notificationSettingRepository;
        this.courseService = courseService;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Begin the process of email summaries
     * i.e. find all active Artemis users that have weekly summaries enabled in their notification settings
     * and initiate the creation of summary emails for each found user.
     */
    @Async
    public void prepareEmailSummaries() {
        checkSecurityUtils();
        // find all Artemis users // Could be improved by getting only still active users
        Set<User> allUsers = new HashSet<>(userRepository.findAllWithGroupsAndAuthorities());
        // filter out users that do not want to receive weekly summaries
        Set<User> filteredUsers = allUsers.stream().filter(this::filterBasedOnUserSettingAndScheduleInterval).collect(Collectors.toSet());
        if (!filteredUsers.isEmpty()) {
            filteredUsers.forEach(this::prepareEmailSummaryForUser);
        }
    }

    /**
     * Prepares all needed information to create the summary email for one user
     * and calls the MailService
     * @param user for whom the summary email should be prepared
     */
    private void prepareEmailSummaryForUser(User user) {
        // Get all courses with exercises, lectures and exams (filtered for given user)
        List<Course> courses = courseService.findAllActiveWithExercisesAndLecturesAndExamsForUser(user);

        // Get released exercises of the last week (which are not yet over, i.e. working due date is not passed yet)
        Set<Exercise> newExercises = getAllExercisesForSummary(courses);

        // More elements that should be displayed in weekly summaries can be extracted here
        // Currently only exercises are used for weekly summaries

        // currently, only weekly summaries are supported -> for daily just add one more case
        if (scheduleInterval.equals(weekly)) {
            mailService.sendWeeklySummaryEmail(user, newExercises);
        }
    }

    /**
     * Filter users based on their user/notification settings and schedule interval
     * e.g. if the schedule interval is weekly -> check for weekly summary setting
     * @param user who should be checked
     * @return true if the setting that corresponds to the used interval is activated else false
     */
    private boolean filterBasedOnUserSettingAndScheduleInterval(User user) {
        // currently, only weekly summaries are supported -> for daily just add one more case
        if (scheduleInterval.equals(weekly)) {
            return checkIfWeeklySummaryIsAllowedByNotificationSettingsForGivenUser(user);
        }
        else
            throw new UnsupportedOperationException("Unsupported scheduleInterval: " + scheduleInterval);
    }

    /**
     * @param courses which exercises will be extracted
     * @return all still active exercises based on the students courses and the scheduleInterval
     * e.g. scheduleInterval = 7 days -> all still active exercises of the last week (7 days)
     */
    private Set<Exercise> getAllExercisesForSummary(List<Course> courses) {
        Set<Exercise> newExercises = new HashSet<>();
        courses.forEach(course -> newExercises.addAll(course.getExercises().stream().filter(this::shouldExerciseBePartOfEmailSummary).collect(Collectors.toSet())));
        return newExercises;
    }

    /**
     * Checks if an exercise should be part of the email summary
     * Exercise should have been released, not yet ended, and the release should have been in the time frame of now - scheduleInterval
     *
     * @param exercise that should be checked
     * @return true if the exercise should be part of the email summary else false
     */
    private boolean shouldExerciseBePartOfEmailSummary(Exercise exercise) {
        if (exercise == null || exercise.getReleaseDate() == null) {
            return false;
        }
        return exercise.isReleased() && exercise.getReleaseDate().isAfter(ZonedDateTime.now().minus(scheduleInterval)) && !exercise.isEnded();
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
        if (weeklySummarySetting == null) {
            return false;
        }
        return weeklySummarySetting.isEmail();
    }

    public void setScheduleInterval(Duration scheduleInterval) {
        this.scheduleInterval = scheduleInterval;
    }

    /**
     * Checks and sets the needed authentication
     */
    private void checkSecurityUtils() {
        if (!SecurityUtils.isAuthenticated()) {
            SecurityUtils.setAuthorizationObject();
        }
    }
}
