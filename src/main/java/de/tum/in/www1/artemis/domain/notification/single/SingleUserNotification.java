package de.tum.in.www1.artemis.domain.notification.single;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.notification.Notification;

/**
 * A SingleUserNotification.
 */
@Entity
@DiscriminatorValue(value = "U")
@JsonTypeName("single")
public class SingleUserNotification extends Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne
    private User recipient;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove
    public User getRecipient() {
        return recipient;
    }

    public SingleUserNotification recipient(User user) {
        this.recipient = user;
        return this;
    }

    public void setRecipient(User user) {
        this.recipient = user;
    }
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    public String getTopic() {
        return "/topic/user/" + getRecipient().getId() + "/notifications";
    }

    public SingleUserNotification() {
    }

    public SingleUserNotification(String text, User author, User recipient, Course course) {
        this.setText(text);
        this.setNotificationDate(ZonedDateTime.now());
        this.setAuthor(author);
        this.setRecipient(recipient);
        this.setCourse(course);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SingleUserNotification singleUserNotification = (SingleUserNotification) o;
        if (singleUserNotification.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), singleUserNotification.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "SingleUserNotification{" + "id=" + getId() + "}";
    }
}
