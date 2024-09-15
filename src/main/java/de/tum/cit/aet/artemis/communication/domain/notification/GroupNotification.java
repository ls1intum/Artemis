package de.tum.cit.aet.artemis.communication.domain.notification;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.GroupNotificationType;
import de.tum.cit.aet.artemis.communication.domain.NotificationPriority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A GroupNotification.
 */
@Entity
@DiscriminatorValue(value = "G")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GroupNotification extends Notification {

    /**
     * Specifies the group : INSTRUCTOR, EDITOR, TA, STUDENT, ...
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private GroupNotificationType type;

    @ManyToOne
    @JsonIgnoreProperties("groupNotifications")
    private Course course;

    // jhipster-needle-entity-add-field - JHipster will add fields here, do not remove

    public GroupNotificationType getType() {
        return type;
    }

    public GroupNotification type(GroupNotificationType type) {
        this.type = type;
        return this;
    }

    public void setType(GroupNotificationType type) {
        this.type = type;
    }

    public Course getCourse() {
        return course;
    }

    public GroupNotification course(Course course) {
        this.course = course;
        return this;
    }

    public GroupNotification() {
    }

    public GroupNotification(Course course, String title, String notificationText, boolean textIsPlaceholder, String[] placeholderValues, User user, GroupNotificationType type) {
        this.setCourse(course);
        this.setType(type);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(notificationText);
        this.setTextIsPlaceholder(textIsPlaceholder);
        this.setPlaceholderValues(placeholderValues);
        this.setAuthor(user);
    }

    public GroupNotification(Course course, String title, String notificationText, boolean textIsPlaceholder, String[] placeholderValues, User user, GroupNotificationType type,
            NotificationPriority priority) {
        this.setCourse(course);
        this.setType(type);
        this.setNotificationDate(ZonedDateTime.now());
        this.setTitle(title);
        this.setText(notificationText);
        this.setTextIsPlaceholder(textIsPlaceholder);
        this.setPlaceholderValues(placeholderValues);
        this.setAuthor(user);
        this.setPriority(priority);
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getTopic() {
        return "/topic/course/" + getCourse().getId() + "/" + getType();
    }

    @Override
    public String toString() {
        return "GroupNotification{" + "id=" + getId() + ", type='" + getType() + "'" + "}";
    }
}
