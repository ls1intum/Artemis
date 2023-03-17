package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.User;

/**
 * A SingleUserNotification.
 */
@Entity
@DiscriminatorValue(value = "U")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SingleUserNotification extends Notification {

    @ManyToOne
    private User recipient;

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User user) {
        this.recipient = user;
    }

    public String getTopic() {
        return "/topic/user/" + getRecipient().getId() + "/notifications";
    }

    public SingleUserNotification() {
    }

    public SingleUserNotification(User recipient, String title, String text) {
        this.setRecipient(recipient);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(text);
    }

    @Override
    public String toString() {
        return "SingleUserNotification{" + "id=" + getId() + "}";
    }
}
