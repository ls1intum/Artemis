package de.tum.cit.aet.artemis.communication.web.push_notification;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;

public record PushNotificationRegisterBody(String token, PushNotificationDeviceType deviceType) {
}
