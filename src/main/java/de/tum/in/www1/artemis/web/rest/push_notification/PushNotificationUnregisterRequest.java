package de.tum.in.www1.artemis.web.rest.push_notification;

import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;

public class PushNotificationUnregisterRequest {

    private String token;

    private PushNotificationDeviceType deviceType;

    public PushNotificationUnregisterRequest(String token, PushNotificationDeviceType deviceType) {
        this.token = token;
        this.deviceType = deviceType;
    }

    public PushNotificationUnregisterRequest() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setDeviceType(PushNotificationDeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public PushNotificationDeviceType getDeviceType() {
        return deviceType;
    }
}
