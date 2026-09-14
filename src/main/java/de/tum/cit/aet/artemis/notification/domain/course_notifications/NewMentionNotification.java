package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.NewMentionPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user they were mentioned in a post or answer post.
 */
@CourseNotificationType(3)
public class NewMentionNotification extends CourseNotification {

    private final NewMentionPayloadDTO payload;

    /**
     * Default constructor used when creating a new post notification.
     */
    public NewMentionNotification(Long courseId, String courseTitle, String courseImageUrl, String postMarkdownContent, String postCreationDate, String postAuthorName, Long postId,
            String replyMarkdownContent, String replyCreationDate, String replyAuthorName, Long replyAuthorId, String replyImageUrl, Long replyId, String channelName,
            Long channelId, boolean replyIsBot) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new NewMentionPayloadDTO(postMarkdownContent, postCreationDate, postAuthorName, postId, replyMarkdownContent, replyCreationDate, replyAuthorName,
                replyAuthorId, replyImageUrl, replyId, channelName, channelId, replyIsBot);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewMentionNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, NewMentionPayloadDTO.class);
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
        return List.of(NotificationChannelOption.EMAIL, NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        String returnUrl = "/courses/" + courseId + "/communication?conversationId=" + payload.channelId() + "&focusPostId=" + payload.postId();

        if (payload.replyId() != null) {
            returnUrl += "&openThreadOnFocus=1&postInThread=" + payload.replyId();
        }

        return returnUrl;
    }

    @Override
    public NewMentionPayloadDTO payload() {
        return payload;
    }
}
