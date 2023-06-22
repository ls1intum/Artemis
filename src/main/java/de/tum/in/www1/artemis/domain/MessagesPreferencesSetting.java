package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Individual MessagesPreferences Setting which combined make the MessagesPreferences Settings (inside the hierarchical structure on the client side)
 * The unique constraint is needed to avoid duplications.
 */
@Entity
@Table(name = "messages_preferences_setting", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "setting_id" }) })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MessagesPreferencesSetting extends DomainObject {

    @Column(name = "setting_id", nullable = false)
    private String settingId;

    @Column(name = "enabled", columnDefinition = "boolean default true", nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("notificationSetting")
    private User user;

    public MessagesPreferencesSetting() {
        // Default empty constructor
    }

    // used to create default settings where both communication channels are supported
    public MessagesPreferencesSetting(boolean enabled, String settingId) {
        this.setEnabled(enabled);
        this.setSettingId(settingId);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSettingId() {
        return settingId;
    }

    public void setSettingId(String settingId) {
        this.settingId = settingId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    private boolean checkUser(User thisUser, User providedUser) {
        if (thisUser == null && providedUser == null) {
            return true;
        }
        if (thisUser != null && providedUser != null) {
            return thisUser.equals(providedUser);
        }
        return false;
    }

    private boolean checkSettingId(String thisSettingId, String providedSettingId) {
        if (thisSettingId == null && providedSettingId == null) {
            return true;
        }
        if (thisSettingId != null) {
            return thisSettingId.equals(providedSettingId);
        }
        return false;
    }
}
