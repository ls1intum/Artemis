package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells a student that they were registered in a tutorial group.
 */
@CourseNotificationType(23)
public class RegisteredToTutorialGroupNotification extends CourseNotification {

    protected String groupTitle;

    protected Long groupId;

    protected String moderatorName;

    /**
     * Default constructor used when creating the notification.
     */
    public RegisteredToTutorialGroupNotification(Long courseId, String courseTitle, String courseImageUrl, String groupTitle, Long groupId, String moderatorName) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.groupTitle = groupTitle;
        this.groupId = groupId;
        this.moderatorName = moderatorName;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public RegisteredToTutorialGroupNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
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
        return List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId + "/tutorial-groups/" + groupId;
    }
}
