package de.tum.in.www1.artemis.domain.notification;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DatabaseNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import jakarta.persistence.*;

/**
 * A GroupNotification.
 */
@Entity
@DiscriminatorValue(value = "G")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GroupNotification extends Notification {

    public GroupNotification() {
    }

    public GroupNotification(Course course, String title, String notificationText, User user, DatabaseNotificationType type) {
        this.setCourse(course);
        this.setType(type);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(notificationText);
        this.setAuthor(user);
    }

    public GroupNotification(Course course, String title, String notificationText, User user, DatabaseNotificationType type, NotificationPriority priority) {
        this.setCourse(course);
        this.setType(type);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(notificationText);
        this.setAuthor(user);
        this.setPriority(priority);
    }

    public String getTopic() {
        return "/topic/course/" + getCourse().getId() + "/" + getType();
    }

    @Override
    public String toString() {
        return "GroupNotification{" + "id=" + getId() + ", type='" + getType() + "'" + "}";
    }
}
