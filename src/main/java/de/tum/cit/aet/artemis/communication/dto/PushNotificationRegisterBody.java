package de.tum.cit.aet.artemis.communication.dto;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;

public record PushNotificationRegisterBody(String token, PushNotificationDeviceType deviceType) {
}
