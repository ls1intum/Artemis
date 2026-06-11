package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;

/**
 * Notification that tells the user Iris has answered their chat message while the chat was not open
 * anywhere (app backgrounded/closed, or the chat closed in the web client). Distributed via websocket
 * (in-app) and push.
 */
@CourseNotificationType(26)
public class IrisResponseNotification extends CourseNotification {

    protected Long sessionId;

    protected String messagePreview;

    protected String chatTitle;

    /**
     * Default constructor used when creating a new notification.
     */
    public IrisResponseNotification(Long courseId, String courseTitle, String courseImageUrl, Long sessionId, String messagePreview, String chatTitle) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.sessionId = sessionId;
        this.messagePreview = messagePreview;
        this.chatTitle = chatTitle;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public IrisResponseNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
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
        return List.of(NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        // TODO
        return "";
    }
}
