package de.tum.cit.aet.artemis.communication.service.notifications.push_notifications;

public record RelayNotificationRequest(String initializationVector, String payloadCipherText, String token) {
}
