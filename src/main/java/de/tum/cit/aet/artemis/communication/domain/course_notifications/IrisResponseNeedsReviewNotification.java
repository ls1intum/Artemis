package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells tutors/instructors that an Iris-generated answer post has a confidence
 * score below the auto-publish threshold and needs to be manually reviewed and approved before
 * it becomes visible to students.
 */
@CourseNotificationType(26)
public class IrisResponseNeedsReviewNotification extends CourseNotification {

    protected String postMarkdownContent;

    protected String postCreationDate;

    protected String postAuthorName;

    protected Long postId;

    protected String replyMarkdownContent;

    protected String replyCreationDate;

    protected Long replyId;

    protected Double replyConfidence;

    protected String channelName;

    protected Long channelId;

    /**
     * Default constructor used when creating a new notification.
     */
    public IrisResponseNeedsReviewNotification(Long courseId, String courseTitle, String courseImageUrl, String postMarkdownContent, String postCreationDate, String postAuthorName,
            Long postId, String replyMarkdownContent, String replyCreationDate, Long replyId, Double replyConfidence, String channelName, Long channelId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.postMarkdownContent = postMarkdownContent;
        this.postCreationDate = postCreationDate;
        this.postAuthorName = postAuthorName;
        this.postId = postId;
        this.replyMarkdownContent = replyMarkdownContent;
        this.replyCreationDate = replyCreationDate;
        this.replyId = replyId;
        this.replyConfidence = replyConfidence;
        this.channelName = channelName;
        this.channelId = channelId;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public IrisResponseNeedsReviewNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
    }

    @Override
    public CourseNotificationCategory getCourseNotificationCategory() {
        return CourseNotificationCategory.IRIS_REVIEW;
    }

    @Override
    public Duration getCleanupDuration() {
        return Duration.ofDays(7);
    }

    @Override
    public List<NotificationChannelOption> getSupportedChannels() {
        return List.of(NotificationChannelOption.WEBAPP);
    }

    @Override
    public String getRelativeWebAppUrl() {
        return "/courses/" + courseId + "/communication?conversationId=" + channelId + "&focusPostId=" + postId + "&openThreadOnFocus=1&postInThread=" + replyId;
    }
}
