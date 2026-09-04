package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.RemovedFromChannelPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user they were removed from a channel.
 */
@CourseNotificationType(20)
public class RemovedFromChannelNotification extends CourseNotification {

    private final RemovedFromChannelPayloadDTO payload;

    /**
     * Default constructor used when creating the notification.
     */
    public RemovedFromChannelNotification(Long courseId, String courseTitle, String courseImageUrl, String channelModerator, String channelName, Long channelId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        // A group chat has no name of its own, so the notification names it the way the client would.
        this.payload = new RemovedFromChannelPayloadDTO(channelModerator, Objects.requireNonNullElse(channelName, "Group Chat"), channelId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public RemovedFromChannelNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, RemovedFromChannelPayloadDTO.class);
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
    public RemovedFromChannelPayloadDTO payload() {
        return payload;
    }
}
