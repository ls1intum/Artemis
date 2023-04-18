package de.tum.in.www1.artemis.service.notifications.push_notifications;

public record RelayNotificationRequest(String initializationVector, String payloadCipherText, String token) {
}
