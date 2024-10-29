package de.tum.cit.aet.artemis.communication.domain.notification;

import java.time.ZonedDateTime;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

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

    public SingleUserNotification(User recipient, String title, String text, boolean textIsPlaceholder, String[] placeholderValues) {
        this.setRecipient(recipient);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(text);
        this.setTextIsPlaceholder(textIsPlaceholder);
        this.setPlaceholderValues(placeholderValues);
    }

    public SingleUserNotification transientAndStringTarget(NotificationTarget transientAndStringTarget) {
        this.setTransientAndStringTarget(transientAndStringTarget);
        return this;
    }

    @Override
    public String toString() {
        return "SingleUserNotification{" + "id=" + getId() + "}";
    }
}
