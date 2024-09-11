package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.communication.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.NotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

@Profile(PROFILE_CORE)
@Service
public class EmailSummaryService {

    private final MailService mailService;

    private final NotificationSettingRepository notificationSettingRepository;

    private final CourseService courseService;

    private final ExerciseRepository exerciseRepository;

    private Duration scheduleInterval;

    private final Duration weekly = Duration.ofDays(7);

    public EmailSummaryService(MailService mailService, NotificationSettingRepository notificationSettingRepository, CourseService courseService,
            ExerciseRepository exerciseRepository) {
        this.mailService = mailService;
        this.notificationSettingRepository = notificationSettingRepository;
        this.courseService = courseService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Begin the process of email summaries
     * i.e. find all active Artemis users that have weekly summaries enabled in their notification settings
     * and initiate the creation of summary emails for each found user.
     */
    @Async
    public void prepareEmailSummariesAsynchronously() {
        checkSecurityUtils();
        Set<User> filteredUsers = findRelevantUsersForSummary();
        if (filteredUsers.isEmpty()) {
            return;
        }

        prepareEmailSummariesForUsers(filteredUsers);
    }

    /**
     * Prepare email summaries for a set of users
     *
     * @param users the users to prepare email summaries for
     */
    public void prepareEmailSummariesForUsers(Set<User> users) {
        checkSecurityUtils();
        // More elements that should be displayed in weekly summaries can be extracted here
        // Currently only exercises are used for weekly summaries

        // load all relevant exercises from the DB once
        Set<Exercise> allExercisesRelevantForSummary = exerciseRepository.findAllExercisesForSummary(ZonedDateTime.now(), ZonedDateTime.now().minusDays(scheduleInterval.toDays()));

        users.forEach(user -> prepareEmailSummaryForUser(user, allExercisesRelevantForSummary));
    }

    /**
     * Prepares all needed information to create the summary email for one user
     * and calls the MailService
     *
     * @param user                                   for whom the summary email should be prepared
     * @param allPossiblyRelevantExercisesForSummary are used to find the relevant exercises for this concrete user
     */
    private void prepareEmailSummaryForUser(User user, Set<Exercise> allPossiblyRelevantExercisesForSummary) {
        // Get all courses with exercises, lectures and exams (filtered for given user)
        Set<Course> courses = courseService.findAllActiveForUser(user);

        // Filter out the relevant exercises for this individual user's summary
        Set<Exercise> relevantExercisesForThisUser = allPossiblyRelevantExercisesForSummary.stream()
                .filter(exercise -> courses.contains(exercise.getCourseViaExerciseGroupOrCourseMember())).collect(Collectors.toSet());

        // currently, only weekly summaries are supported -> for daily just add one more case
        if (scheduleInterval.equals(weekly)) {
            mailService.sendWeeklySummaryEmail(user, relevantExercisesForThisUser);
        }
    }

    /**
     * Finds all users that should receive this summary email based on their user/notification settings and schedule interval
     * e.g. if the schedule interval is weekly -> check for weekly summary setting
     *
     * @return all users that should receive this summary email
     */
    public Set<User> findRelevantUsersForSummary() {
        checkSecurityUtils();
        // currently, only weekly summaries are supported -> for daily just add one more case
        if (scheduleInterval.equals(weekly)) {
            return notificationSettingRepository
                    .findAllUsersWhoEnabledSpecifiedEmailNotificationSettingWithEagerGroupsAndAuthorities(NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        }
        else {
            throw new UnsupportedOperationException("Unsupported scheduleInterval: " + scheduleInterval);
        }
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
