package de.tum.cit.aet.artemis.communication.domain.notification;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.SystemNotificationType;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A SystemNotification.
 */
@Entity
@DiscriminatorValue("S")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SystemNotification extends Notification {

    @Column(name = "title")
    private String title;

    @Column(name = "text")
    private String text;

    @Column(name = "notification_date")
    private ZonedDateTime notificationDate;

    @ManyToOne
    private User author;

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

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User user) {
        this.author = user;
    }

    @Override
    public String toString() {
        return "SystemNotification{" + "id=" + getId() + ", expireDate='" + getExpireDate() + "'" + ", type='" + getType() + "'" + "}";
    }
}
