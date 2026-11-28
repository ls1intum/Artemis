package de.tum.cit.aet.artemis.notification.domain.course_notifications;

import java.time.ZonedDateTime;
import java.util.Map;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.dto.payload.AttachmentChangedPayloadDTO;
import de.tum.cit.aet.artemis.notification.util.CourseNotificationPayloads;

/**
 * Notification that tells the user there was a change in an attachment in a lecture or exercise.
 */
@CourseNotificationType(10)
public class AttachmentChangedNotification extends CourseNotification {

    private final AttachmentChangedPayloadDTO payload;

    /**
     * Default constructor used when creating a new attachment changed notification. The notification text is the
     * message the editor wrote for students about the change, {@code null} when they wrote none.
     */
    public AttachmentChangedNotification(Long courseId, String courseTitle, String courseImageUrl, String attachmentName, String unitName, Long exerciseId, Long lectureId,
            String notificationText) {
        super(null, courseId, courseTitle, courseImageUrl, ZonedDateTime.now());
        this.payload = new AttachmentChangedPayloadDTO(attachmentName, unitName, exerciseId, lectureId, notificationText);
    }

    /**
     * Constructor used when loading the existing notification from the database.
     */
    public AttachmentChangedNotification(Long notificationId, Long courseId, ZonedDateTime creationDate, Map<String, String> parameters) {
        super(notificationId, courseId, creationDate, parameters);
        this.payload = CourseNotificationPayloads.parse(parameters, AttachmentChangedPayloadDTO.class);
    }

    @Override
    protected CourseNotificationDeliveryPolicy getDeliveryPolicy() {
        return CourseNotificationDeliveryPolicy.IN_APP_GENERAL;
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
