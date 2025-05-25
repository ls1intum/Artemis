package de.tum.cit.aet.artemis.communication.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.communication.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationChannelOption;

/**
 * Notification that tells the user there was a change in an attachment in a lecture or exercise.
 */
@CourseNotificationType(10)
public class AttachmentChangedNotification extends CourseNotification {

    protected String attachmentName;

    protected String unitName;

    protected Long exerciseId;

    protected Long lectureId;

    /**
     * Default constructor used when creating a new post notification.
     */
    public AttachmentChangedNotification(Long courseId, String courseTitle, String courseImageUrl, String attachmentName, String unitName, Long exerciseId, Long lectureId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.attachmentName = attachmentName;
        this.unitName = unitName;
        this.exerciseId = exerciseId;
        this.lectureId = lectureId;
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public AttachmentChangedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
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
        return List.of(NotificationChannelOption.WEBAPP, NotificationChannelOption.PUSH);
    }

    @Override
    public String getRelativeWebAppUrl() {
        String urlPostfix = "";

        if (exerciseId != null) {
            urlPostfix = "/exercises/" + exerciseId;
        }
        else if (lectureId != null) {
            urlPostfix = "/lectures/" + lectureId;
        }

        return "/courses/" + courseId + urlPostfix;
    }
}
