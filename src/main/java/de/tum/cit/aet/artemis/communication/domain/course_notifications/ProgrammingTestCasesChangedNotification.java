package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user that test cases changed in an exercise.
 */
@CourseNotificationType(16)
public class ProgrammingTestCasesChangedNotification extends CourseNotification {

    protected Long exerciseId;

    protected String exerciseTitle;

    /**
     * Default constructor used when creating the notification
     */
    public ProgrammingTestCasesChangedNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.exerciseId = exerciseId;
        this.exerciseTitle = exerciseTitle;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public ProgrammingTestCasesChangedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
    }

    @Override
    public CourseNotificationCategory getCourseNotificationCategory() {
        return CourseNotificationCategory.GENERAL;
    }

    @Override
    public Duration getCleanupDuration() {
        return Duration.ofDays(7);
    }

    @Override
    public List<NotificationChannelOption> getSupportedChannels() {
        return List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId + "/exercises/" + exerciseId;
    }
}
