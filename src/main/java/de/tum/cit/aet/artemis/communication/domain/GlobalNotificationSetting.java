package de.tum.cit.aet.artemis.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "global_notification_setting", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "global_notification_type" }))
public class GlobalNotificationSetting extends DomainObject {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "global_notification_type", nullable = false)
    private GlobalNotificationType notificationType;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public GlobalNotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(GlobalNotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
