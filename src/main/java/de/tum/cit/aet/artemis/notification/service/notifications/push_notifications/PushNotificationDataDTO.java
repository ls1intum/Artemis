package de.tum.cit.aet.artemis.notification.service.notifications.push_notifications;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationSerializedDTO;

/**
 * This DTO represents the data that is encrypted and sent to the hermes service.
 */
public record PushNotificationDataDTO(int version, CourseNotificationSerializedDTO courseNotificationDTO) {

    public PushNotificationDataDTO(CourseNotificationSerializedDTO courseNotificationDTO) {
        this(Constants.PUSH_NOTIFICATION_VERSION, courseNotificationDTO);
    }
}
