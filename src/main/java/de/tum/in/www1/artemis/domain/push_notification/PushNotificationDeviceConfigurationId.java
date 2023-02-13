package de.tum.in.www1.artemis.domain.push_notification;

import java.io.Serializable;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.User;

public class PushNotificationDeviceConfigurationId implements Serializable {

    private Long owner;

    private String token;

    private PushNotificationDeviceType deviceType;

    public PushNotificationDeviceConfigurationId(User owner, String token, PushNotificationDeviceType deviceType) {
        this.owner = owner.getId();
        this.token = token;
        this.deviceType = deviceType;
    }

    public PushNotificationDeviceConfigurationId() {
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

    public void setDeviceType(PushNotificationDeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public Long getOwner() {
        return owner;
    }

    public void setOwner(Long owner) {
        this.owner = owner;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public PushNotificationDeviceType getDeviceType() {
        return deviceType;
    }
}
