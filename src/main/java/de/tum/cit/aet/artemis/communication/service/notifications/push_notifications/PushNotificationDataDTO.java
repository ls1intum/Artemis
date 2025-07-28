package de.tum.cit.aet.artemis.communication.service.notifications.push_notifications;

import de.tum.cit.aet.artemis.communication.dto.CourseNotificationSerializedDTO;
import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * This DTO represents the data that is encrypted and sent to the hermes service. All values except courseNotificationDTO
 * are part of the old notification api. As soon as iOS and Android are able to process the courseNotificationDTO by itself
 * we can remove the initial values in this record.
 */
public record PushNotificationDataDTO(int version, CourseNotificationSerializedDTO courseNotificationDTO) {

    public PushNotificationDataDTO(CourseNotificationSerializedDTO courseNotificationDTO) {
        this(Constants.PUSH_NOTIFICATION_VERSION, courseNotificationDTO);
    }
}
