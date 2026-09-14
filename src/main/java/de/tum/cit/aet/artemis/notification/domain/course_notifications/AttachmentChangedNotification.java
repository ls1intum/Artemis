package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import de.tum.cit.aet.artemis.notification.dto.payload.AttachmentChangedPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user there was a change in an attachment in a lecture or exercise.
 */
@CourseNotificationType(10)
public class AttachmentChangedNotification extends CourseNotification {

    private final AttachmentChangedPayloadDTO payload;

    /**
     * Default constructor used when creating a new post notification.
     */
    public AttachmentChangedNotification(Long courseId, String courseTitle, String courseImageUrl, String attachmentName, String unitName, Long exerciseId, Long lectureId) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new AttachmentChangedPayloadDTO(attachmentName, unitName, exerciseId, lectureId);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public AttachmentChangedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, AttachmentChangedPayloadDTO.class);
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

        if (payload.exerciseId() != null) {
            urlPostfix = "/exercises/" + payload.exerciseId();
        }
        else if (payload.lectureId() != null) {
            urlPostfix = "/lectures/" + payload.lectureId();
        }

        return "/courses/" + courseId + urlPostfix;
    }

    @Override
    public AttachmentChangedPayloadDTO payload() {
        return payload;
    }
}
