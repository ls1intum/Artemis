package de.tum.cit.aet.artemis.course_notification.domain.notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.course_notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.course_notification.domain.NotificationSettingOption;

/**
 * Notification that tells the user there was a new post in a channel of any type. Announcement posts and thread answers
 * are sent via different notifications.
 */
@CourseNotificationType(1)
public class NewPostNotification extends CourseNotification {

    protected String courseTitle;

    protected Long postId;

    protected String postMarkdownContent;

    protected Long channelId;

    protected String channelName;

    protected String channelType;

    protected String authorName;

    protected String authorImageUrl;

    protected Long authorId;

    /**
     * Default constructor used when creating a new post notification.
     */
    public NewPostNotification(Long courseId, String courseTitle, Long postId, String postMarkdownContent, Long channelId, String channelName, String channelType,
            String authorName, String authorImageUrl, Long authorId) {
        super(courseId, ZonedDateTime.now());
        this.courseTitle = courseTitle;
        this.postId = postId;
        this.postMarkdownContent = postMarkdownContent;
        this.channelName = channelName;
        this.channelType = channelType;
        this.authorName = authorName;
        this.authorImageUrl = authorImageUrl;
        this.authorId = authorId;
        this.channelId = channelId;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public NewPostNotification(Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(courseId, creationDate, parameters);
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
    public List<NotificationSettingOption> getSupportedChannels() {
        return List.of(NotificationSettingOption.WEBAPP, NotificationSettingOption.PUSH);
    }
}
