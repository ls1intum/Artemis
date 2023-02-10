package de.tum.in.www1.artemis.web.rest.push_notification;

public class PushNotificationRegisterDTO {

    private String secretKey;

    private String algorithm;

    public PushNotificationRegisterDTO(String secretKey, String algorithm) {
        this.secretKey = secretKey;
        this.algorithm = algorithm;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getAlgorithm() {
        return algorithm;
    }
}
