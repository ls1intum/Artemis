package de.tum.cit.aet.artemis.domain.push_notification;

import java.io.Serializable;
import java.util.Objects;

import de.tum.cit.aet.artemis.domain.User;

/**
 * The PrimaryKey used for PushNotificationDeviceConfiguration
 */
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
        // needed for JPA
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        PushNotificationDeviceConfigurationId that = (PushNotificationDeviceConfigurationId) object;
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
