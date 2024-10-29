package de.tum.cit.aet.artemis.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Individual Notification Setting which combined make the Notification Settings (inside the hierarchical structure on the client side)
 * The unique constraint is needed to avoid duplications.
 */
@Entity
@Table(name = "notification_setting", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "setting_id" }) })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class NotificationSetting extends DomainObject {

    @Column(name = "setting_id", nullable = false)
    private String settingId;

    @Column(name = "webapp", columnDefinition = "boolean default true", nullable = false)
    private boolean webapp = true;

    @Column(name = "email", columnDefinition = "boolean default false", nullable = false)
    private boolean email = false;

    @Column(name = "push", columnDefinition = "boolean default true", nullable = false)
    private boolean push = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("notificationSetting")
    private User user;

    public NotificationSetting() {
        // Default empty constructor
    }

    // used to create default settings where both communication channels are supported
    public NotificationSetting(boolean webapp, boolean email, boolean push, String settingId) {
        this.setWebapp(webapp);
        this.setEmail(email);
        this.setSettingId(settingId);
        this.setPush(push);
    }

    public NotificationSetting(User user, boolean webapp, boolean email, boolean push, String settingId) {
        this.setUser(user);
        this.setWebapp(webapp);
        this.setEmail(email);
        this.setSettingId(settingId);
        this.setPush(push);
    }

    public String getSettingId() {
        return settingId;
    }

    public void setSettingId(String settingId) {
        this.settingId = settingId;
    }

    public boolean isWebapp() {
        return webapp;
    }

    public void setWebapp(boolean webapp) {
        this.webapp = webapp;
    }

    public boolean isEmail() {
        return email;
    }

    public void setEmail(boolean email) {
        this.email = email;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setPush(boolean push) {
        this.push = push;
    }

    public boolean isPush() {
        return push;
    }

    @Override
    public String toString() {
        return "NotificationSetting{" + ", settingId='" + settingId + '\'' + ", webapp=" + webapp + ", email=" + email + ", push=" + push + ", user=" + user + '}';
    }
}
