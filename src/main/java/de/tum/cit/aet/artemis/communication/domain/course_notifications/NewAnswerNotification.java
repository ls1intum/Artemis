package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user there was a new thread reply in a channel of any type.
 */
@CourseNotificationType(2)
public class NewAnswerNotification extends CourseNotification {

    // "Post" = parent post, "Reply" = answer post
    protected String postMarkdownContent;

    protected String postCreationDate;

    protected String postAuthorName;

    protected Long postId;

    protected String replyMarkdownContent;

    protected String replyCreationDate;

    protected String replyAuthorName;

    protected Long replyAuthorId;

    protected String replyImageUrl;

    protected Long replyId;

    protected String channelName;

    protected Long channelId;

    /**
     * Default constructor used when creating a new post notification.
     */
    public NewAnswerNotification(Long courseId, String courseTitle, String courseImageUrl, String postMarkdownContent, String postCreationDate, String postAuthorName, Long postId,
            String replyMarkdownContent, String replyCreationDate, String replyAuthorName, Long replyAuthorId, String replyImageUrl, Long replyId, String channelName,
            Long channelId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.postMarkdownContent = postMarkdownContent;
        this.postCreationDate = postCreationDate;
        this.postAuthorName = postAuthorName;
        this.postId = postId;
        this.replyMarkdownContent = replyMarkdownContent;
        this.replyCreationDate = replyCreationDate;
        this.replyAuthorName = replyAuthorName;
        this.replyAuthorId = replyAuthorId;
        this.replyImageUrl = replyImageUrl;
        this.replyId = replyId;
        this.channelName = channelName;
        this.channelId = channelId;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewAnswerNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
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
        return "/courses/" + courseId + "/communication?conversationId=" + channelId + "&focusPostId=" + postId + "&openThreadOnFocus=1&postInThread=" + replyId;
    }
}
