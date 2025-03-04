package de.tum.cit.aet.artemis.communication.domain.push_notification;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Model for saving PushNotification DeviceTokens for native clients
 */
@Entity
@Table(name = "push_notification_device_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@IdClass(PushNotificationDeviceConfigurationId.class)
public class PushNotificationDeviceConfiguration {

    @Id
    @Column(name = "token")
    private String token;

    @Id
    @Column(name = "device_type")
    private PushNotificationDeviceType deviceType;

    @Enumerated
    @Column(name = "api_type")
    private PushNotificationApiType apiType;

    @Column(name = "expiration_date")
    private Date expirationDate;

    @Column(name = "secret_key")
    private byte[] secretKey;

    @Column(name = "version_code")
    private String versionCode;

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User owner;

    public PushNotificationDeviceConfiguration(String token, PushNotificationDeviceType deviceType, Date expirationDate, byte[] secretKey, User owner) {
        this.token = token;
        this.deviceType = deviceType;
        this.expirationDate = expirationDate;
        this.secretKey = secretKey;
        this.owner = owner;
    }

    public PushNotificationDeviceConfiguration(String token, PushNotificationDeviceType deviceType, Date expirationDate, byte[] secretKey, User owner,
            PushNotificationApiType apiType, String versionCode) {
        this.token = token;
        this.deviceType = deviceType;
        this.expirationDate = expirationDate;
        this.secretKey = secretKey;
        this.owner = owner;
        this.apiType = apiType;
        this.versionCode = versionCode;
    }

    public PushNotificationDeviceConfiguration() {
        // needed for JPA
    }

    public String getToken() {
        return token;
    }

    public PushNotificationDeviceType getDeviceType() {
        return deviceType;
    }

    public byte[] getSecretKey() {
        return secretKey;
    }

    public User getOwner() {
        return owner;
    }

    public Date getExpirationDate() {
        return expirationDate;
    }

    public void setSecretKey(byte[] secretKey) {
        this.secretKey = secretKey;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setDeviceType(PushNotificationDeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public void setExpirationDate(Date expirationDate) {
        this.expirationDate = expirationDate;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public PushNotificationApiType getApiType() {
        return apiType;
    }

    public String getVersionCode() {
        return versionCode;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        PushNotificationDeviceConfiguration that = (PushNotificationDeviceConfiguration) object;
        // Use compareTo rather than equals for dates to ensure timestamps and dates with the same time are considered equal
        // This is caused by Java internal design having different classes for Date (java.util) and Timestamp (java.sql)
        return token.equals(that.token) && deviceType == that.deviceType && expirationDate.compareTo(that.expirationDate) == 0 && Arrays.equals(secretKey, that.secretKey)
                && owner.equals(that.owner);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(token, deviceType, expirationDate, owner);
        result = 31 * result + Arrays.hashCode(secretKey);
        return result;
    }

    @Override
    public String toString() {
        return "PushNotificationDeviceConfiguration{" + "token='" + token + '\'' + ", deviceType=" + deviceType + ", expirationDate=" + expirationDate + ", secretKey="
                + Arrays.toString(secretKey) + ", owner=" + owner + '}';
    }
}
