package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user that a channel they are in was deleted.
 */
@CourseNotificationType(18)
public class ChannelDeletedNotification extends CourseNotification {

    protected String deletingUser;

    protected String channelName;

    /**
     * Default constructor used when creating the notification.
     */
    public ChannelDeletedNotification(Long courseId, String courseTitle, String courseImageUrl, String deletingUser, String channelName) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.deletingUser = deletingUser;
        this.channelName = Objects.requireNonNullElse(channelName, "Group Chat");
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public ChannelDeletedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
    }

    @Override
    public CourseNotificationCategory getCourseNotificationCategory() {
        return CourseNotificationCategory.COMMUNICATION;
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
        return "/courses/" + courseId;
    }
}
