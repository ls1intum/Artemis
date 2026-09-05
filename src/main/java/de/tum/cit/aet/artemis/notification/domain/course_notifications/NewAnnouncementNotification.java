package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.NewAnnouncementPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user there was a new announcement. These get distributed via push, websocket an e-mail.
 */
@CourseNotificationType(4)
public class NewAnnouncementNotification extends CourseNotification {

    private final NewAnnouncementPayloadDTO payload;

    /**
     * Default constructor used when creating a new post notification.
     */
    public NewAnnouncementNotification(Long courseId, String courseTitle, String courseImageUrl, Long postId, String postTitle, String postMarkdownContent, String authorName,
            String authorImageUrl, Long authorId, Long channelId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new NewAnnouncementPayloadDTO(postId, postTitle, postMarkdownContent, authorName, authorImageUrl, authorId, channelId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewAnnouncementNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, NewAnnouncementPayloadDTO.class);
    }

    @Override
    public CourseNotificationCategory getCourseNotificationCategory() {
        return CourseNotificationCategory.COMMUNICATION;
    }

    @Override
    public Duration getCleanupDuration() {
        return Duration.ofDays(30);
    }

    @Override
    public List<NotificationChannelOption> getSupportedChannels() {
        return List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId + "/communication?conversationId=" + payload.channelId() + "&focusPostId=" + payload.postId();
    }

    @Override
    public NewAnnouncementPayloadDTO payload() {
        return payload;
    }
}
