package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.IrisResponsePayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user Iris has answered their chat message while the chat was not open
 * anywhere (app backgrounded/closed, or the chat closed in the web client). Distributed via websocket
 * (in-app) and push.
 */
@CourseNotificationType(27)
public class IrisResponseNotification extends CourseNotification {

    private final IrisResponsePayloadDTO payload;

    /**
     * Default constructor used when creating a new notification.
     */
    public IrisResponseNotification(Long courseId, String courseTitle, String courseImageUrl, Long sessionId, String messagePreview, String chatTitle) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new IrisResponsePayloadDTO(sessionId, messagePreview, chatTitle);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public IrisResponseNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, IrisResponsePayloadDTO.class);
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
        return List.of(NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId + "/iris?sessionId=" + payload.sessionId();
    }

    @Override
    public IrisResponsePayloadDTO payload() {
        return payload;
    }
}
