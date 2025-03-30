package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user that a duplicate test case was found.
 */
@CourseNotificationType(12)
public class DuplicateTestCaseNotification extends CourseNotification {

    protected Long exerciseId;

    protected String exerciseTitle;

    protected String releaseDate;

    protected String dueDate;

    /**
     * Default constructor used when creating a new post notification.
     */
    public DuplicateTestCaseNotification(Long courseId, String courseTitle, String courseImageUrl, Long exerciseId, String exerciseTitle, String releaseDate, String dueDate) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.exerciseId = exerciseId;
        this.exerciseTitle = exerciseTitle;
        this.releaseDate = releaseDate;
        this.dueDate = dueDate;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public DuplicateTestCaseNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
    }

    @Override
    public CourseNotificationCategory getCourseNotificationCategory() {
        return CourseNotificationCategory.GENERAL;
    }

    @Override
    public Duration getCleanupDuration() {
        return Duration.ofDays(14);
    }

    @Override
    public List<NotificationChannelOption> getSupportedChannels() {
        return List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId + "/exercises/" + exerciseId;
    }
}
