package de.tum.cit.aet.artemis.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "global_notification_setting", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "global_notification_type" }))
public class GlobalNotificationSetting extends DomainObject {

    @NonNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @NonNull
    @Column(name = "global_notification_type", nullable = false, columnDefinition = "varchar(20)")
    private GlobalNotificationType notificationType;

    @NonNull
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
