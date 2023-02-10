package de.tum.in.www1.artemis.web.rest.push_notification;

import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;

public class PushNotificationRegisterBody {

    private final String token;

    private final PushNotificationDeviceType deviceType;

    public PushNotificationRegisterBody(String token, PushNotificationDeviceType deviceType) {
        this.token = token;
        this.deviceType = deviceType;
    }

    public String getToken() {
        return token;
    }

    public PushNotificationDeviceType getDeviceType() {
        return deviceType;
    }
}
