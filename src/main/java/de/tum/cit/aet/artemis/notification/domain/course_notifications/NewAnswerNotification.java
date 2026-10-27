package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.ZonedDateTime;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.dto.payload.NewAnswerPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user there was a new thread reply in a channel of any type.
 */
@CourseNotificationType(2)
public class NewAnswerNotification extends CourseNotification {

    private final NewAnswerPayloadDTO payload;

    // "Post" = parent post, "Reply" = answer post

    /**
     * Default constructor used when creating a new post notification.
     */
    public NewAnswerNotification(Long courseId, String courseTitle, String courseImageUrl, String postMarkdownContent, String postCreationDate, String postAuthorName, Long postId,
            String replyMarkdownContent, String replyCreationDate, String replyAuthorName, Long replyAuthorId, String replyImageUrl, Long replyId, String channelName,
            Long channelId, boolean replyIsBot) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new NewAnswerPayloadDTO(postMarkdownContent, postCreationDate, postAuthorName, postId, replyMarkdownContent, replyCreationDate, replyAuthorName,
                replyAuthorId, replyImageUrl, replyId, channelName, channelId, replyIsBot);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewAnswerNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, NewAnswerPayloadDTO.class);
    }

    @Override
    protected CourseNotificationDeliveryPolicy getDeliveryPolicy() {
        return CourseNotificationDeliveryPolicy.COMMUNICATION;
    }

    @Override
    public String getRelativeWebAppUrl() {
        // messageId is the parent post that seeds the thread; focusReplyId highlights the reply within it.
        return "/courses/" + courseId + "/communication?conversationId=" + payload.channelId() + "&focusPostId=" + payload.postId() + "&openThreadOnFocus=1&messageId="
                + payload.postId() + "&focusReplyId=" + payload.replyId();
    }

    @Override
    public NewAnswerPayloadDTO payload() {
        return payload;
    }
}
