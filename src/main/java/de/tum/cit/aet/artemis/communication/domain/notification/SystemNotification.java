package de.tum.cit.aet.artemis.communication.domain.notification;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A SystemNotification.
 */
@Entity
@Table(name = "system_notification")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SystemNotification extends DomainObject {

    @Column(name = "title")
    private String title;

    @Column(name = "text")
    private String text;

    @Column(name = "notification_date")
    private ZonedDateTime notificationDate;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ZonedDateTime getNotificationDate() {
        return notificationDate;
    }

    public void setNotificationDate(ZonedDateTime notificationDate) {
        this.notificationDate = notificationDate;
    }

    @Override
    public String toString() {
        return "SystemNotification{" + "id=" + getId() + ", expireDate='" + getExpireDate() + "'" + ", type='" + getType() + "'" + "}";
    }
}
