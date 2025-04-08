package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user they were added to a channel.
 */
@CourseNotificationType(19)
public class AddedToChannelNotification extends CourseNotification {

    protected String channelModerator;

    protected String channelName;

    protected Long channelId;

    /**
     * Default constructor used when creating the notification.
     */
    public AddedToChannelNotification(Long courseId, String courseTitle, String courseImageUrl, String channelModerator, String channelName, Long channelId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.channelModerator = channelModerator;
        this.channelName = channelName;
        this.channelId = channelId;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public AddedToChannelNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
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
        return "/courses/" + courseId + "/communication?conversationId=" + channelId;
    }
}
