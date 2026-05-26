package de.tum.cit.aet.artemis.notification.dto;

import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;

public record PushNotificationUnregisterRequest(String token, PushNotificationDeviceType deviceType) {
}
