package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.NewPostPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user there was a new post in a channel of any type. Announcement posts and thread answers
 * are sent via different notifications.
 */
@CourseNotificationType(1)
public class NewPostNotification extends CourseNotification {

    private final NewPostPayloadDTO payload;

    /**
     * Default constructor used when creating a new post notification.
     */
    public NewPostNotification(Long courseId, String courseTitle, String courseImageUrl, Long postId, String postMarkdownContent, Long channelId, String channelName,
            String channelType, String authorName, String authorImageUrl, Long authorId, boolean authorIsBot) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new NewPostPayloadDTO(postId, postMarkdownContent, channelId, channelName, channelType, authorName, authorImageUrl, authorId, authorIsBot);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewPostNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, NewPostPayloadDTO.class);
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
        return "/courses/" + courseId + "/communication?conversationId=" + payload.channelId() + "&focusPostId=" + payload.postId();
    }

    @Override
    public NewPostPayloadDTO payload() {
        return payload;
    }
}
