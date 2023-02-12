package de.tum.in.www1.artemis.domain.push_notification;

import java.io.Serializable;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.User;

public class PushNotificationDeviceConfigurationId implements Serializable {

    private User owner;

    private String token;

    private PushNotificationDeviceType deviceType;

    public PushNotificationDeviceConfigurationId(User owner, String token, PushNotificationDeviceType deviceType) {
        this.owner = owner;
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
        return owner.equals(that.owner) && token.equals(that.token) && deviceType == that.deviceType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, token, deviceType);
    }
}
