package de.tum.cit.aet.artemis.web.rest.push_notification;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;

public record PushNotificationRegisterBody(String token, PushNotificationDeviceType deviceType) {
}
