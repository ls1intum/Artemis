package de.tum.in.www1.artemis.domain.push_notification;

import java.io.Serializable;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.User;

public class PushNotificationDeviceConfigurationId implements Serializable {

    private User user;

    private String token;

    private PushNotificationDeviceType deviceType;

    public PushNotificationDeviceConfigurationId(User user, String token, PushNotificationDeviceType deviceType) {
        this.user = user;
        this.token = token;
        this.deviceType = deviceType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PushNotificationDeviceConfigurationId that = (PushNotificationDeviceConfigurationId) o;
        return user.equals(that.user) && token.equals(that.token) && deviceType == that.deviceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, token, deviceType);
    }
}
