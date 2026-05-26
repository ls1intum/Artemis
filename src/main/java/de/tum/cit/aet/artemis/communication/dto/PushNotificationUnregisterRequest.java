package de.tum.cit.aet.artemis.communication.dto;

import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceType;

public record PushNotificationUnregisterRequest(String token, PushNotificationDeviceType deviceType) {
}
