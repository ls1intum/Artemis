package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.IrisResponseNeedsReviewPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells tutors/instructors that an Iris-generated answer post has a confidence
 * score below the auto-publish threshold and needs to be manually reviewed and approved before
 * it becomes visible to students.
 */
@CourseNotificationType(26)
public class IrisResponseNeedsReviewNotification extends CourseNotification {

    private final IrisResponseNeedsReviewPayloadDTO payload;

    /**
     * Default constructor used when creating a new notification.
     */
    public IrisResponseNeedsReviewNotification(Long courseId, String courseTitle, String courseImageUrl, String postMarkdownContent, String postCreationDate, String postAuthorName,
            Long postId, String replyMarkdownContent, String replyCreationDate, Long replyId, Double replyConfidence, String channelName, Long channelId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new IrisResponseNeedsReviewPayloadDTO(postMarkdownContent, postCreationDate, postAuthorName, postId, replyMarkdownContent, replyCreationDate, replyId,
                replyConfidence, channelName, channelId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public IrisResponseNeedsReviewNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, IrisResponseNeedsReviewPayloadDTO.class);
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
        // messageId is the parent post that seeds the thread; focusReplyId highlights the pending Iris reply within it.
        return "/courses/" + courseId + "/communication?conversationId=" + payload.channelId() + "&focusPostId=" + payload.postId() + "&openThreadOnFocus=1&messageId="
                + payload.postId() + "&focusReplyId=" + payload.replyId();
    }

    @Override
    public IrisResponseNeedsReviewPayloadDTO payload() {
        return payload;
    }
}
