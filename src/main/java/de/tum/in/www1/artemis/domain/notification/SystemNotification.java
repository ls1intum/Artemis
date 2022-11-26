package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.*;

/**
 * A SystemNotification.
 */
@Entity
@DiscriminatorValue(value = "S")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SystemNotification extends Notification {

    @Column(name = "expire_date")
    private ZonedDateTime expireDate;

    public ZonedDateTime getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(ZonedDateTime expireDate) {
        this.expireDate = expireDate;
    }

    @Override
    public String toString() {
        return "SystemNotification{" + "id=" + getId() + ", expireDate='" + getExpireDate() + "'" + ", type='" + getType() + "'" + "}";
    }
}
