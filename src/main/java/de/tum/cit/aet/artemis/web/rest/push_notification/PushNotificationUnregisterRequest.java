package de.tum.cit.aet.artemis.web.rest.push_notification;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;

public record PushNotificationUnregisterRequest(String token, PushNotificationDeviceType deviceType) {
}
