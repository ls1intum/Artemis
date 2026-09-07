package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.ChannelDeletedPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user that a channel they are in was deleted.
 */
@CourseNotificationType(18)
public class ChannelDeletedNotification extends CourseNotification {

    private final ChannelDeletedPayloadDTO payload;

    /**
     * Default constructor used when creating the notification.
     */
    public ChannelDeletedNotification(Long courseId, String courseTitle, String courseImageUrl, String deletingUser, String channelName) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        // A group chat has no name of its own, so the notification names it the way the client would.
        this.payload = new ChannelDeletedPayloadDTO(deletingUser, Objects.requireNonNullElse(channelName, "Group Chat"));
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public ChannelDeletedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, ChannelDeletedPayloadDTO.class);
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

    @Override
    public ChannelDeletedPayloadDTO payload() {
        return payload;
    }
}
