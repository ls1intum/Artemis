package de.tum.in.www1.artemis.web.rest.push_notification;

import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;

public record PushNotificationRegisterBody(String token, PushNotificationDeviceType deviceType) {
}
