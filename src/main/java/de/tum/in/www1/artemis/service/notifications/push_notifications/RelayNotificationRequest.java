package de.tum.in.www1.artemis.service.notifications.push_notifications;

public class RelayNotificationRequest {

    private final String initializationVector;

    private final String payloadCipherText;

    private final String token;

    public RelayNotificationRequest(String initializationVector, String payloadCipherText, String token) {
        this.initializationVector = initializationVector;
        this.payloadCipherText = payloadCipherText;
        this.token = token;
    }

    public String getInitializationVector() {
        return initializationVector;
    }

    public String getPayloadCipherText() {
        return payloadCipherText;
    }

    public String getToken() {
        return token;
    }
}
