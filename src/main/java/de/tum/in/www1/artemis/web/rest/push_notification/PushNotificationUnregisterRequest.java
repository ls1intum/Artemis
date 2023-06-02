package de.tum.in.www1.artemis.web.rest.push_notification;

import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;

public record PushNotificationUnregisterRequest(String token, PushNotificationDeviceType deviceType) {
}
