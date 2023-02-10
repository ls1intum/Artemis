package de.tum.in.www1.artemis.domain.push_notification;

import java.util.Date;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import de.tum.in.www1.artemis.domain.User;

@Entity
@Table(name = "pushNotificationDeviceConfiguration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@IdClass(PushNotificationDeviceConfigurationId.class)
public class PushNotificationDeviceConfiguration {

    @Id
    @Column(name = "token")
    private String token;

    @Id
    @Column(name = "device_type")
    private PushNotificationDeviceType deviceType;

    @Column(name = "expiration_date")
    private Date expirationDate;

    @Column(name = "secret_key")
    private byte[] secretKey;

    @Id
    @ManyToOne
    private User owner;

    public PushNotificationDeviceConfiguration(String token, PushNotificationDeviceType deviceType, Date expirationDate, byte[] secretKey, User owner) {
        this.token = token;
        this.deviceType = deviceType;
        this.expirationDate = expirationDate;
        this.secretKey = secretKey;
        this.owner = owner;
    }

    public PushNotificationDeviceConfiguration() {
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
}
