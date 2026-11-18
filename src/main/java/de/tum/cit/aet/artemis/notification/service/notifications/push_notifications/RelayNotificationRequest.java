package de.tum.cit.aet.artemis.notification.service.notifications.push_notifications;

public record RelayNotificationRequest(String initializationVector, String payloadCipherText, String token, short apiType) {
}
