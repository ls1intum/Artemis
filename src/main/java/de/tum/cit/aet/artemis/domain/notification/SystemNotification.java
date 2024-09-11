package de.tum.cit.aet.artemis.domain.notification;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.enumeration.SystemNotificationType;

/**
 * A SystemNotification.
 */
@Entity
@DiscriminatorValue(value = "S")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SystemNotification extends Notification {

    @Column(name = "expire_date")
    private ZonedDateTime expireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private SystemNotificationType type;

    public ZonedDateTime getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(ZonedDateTime expireDate) {
        this.expireDate = expireDate;
    }

    public SystemNotificationType getType() {
        return type;
    }

    public void setType(SystemNotificationType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "SystemNotification{" + "id=" + getId() + ", expireDate='" + getExpireDate() + "'" + ", type='" + getType() + "'" + "}";
    }
}
