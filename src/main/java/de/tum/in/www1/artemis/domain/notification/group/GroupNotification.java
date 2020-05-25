package de.tum.in.www1.artemis.domain.notification.group;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Objects;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonTypeName;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.notification.Notification;

/**
 * A GroupNotification.
 */
@Entity
@DiscriminatorValue(value = "G")
@JsonTypeName("group")
public class GroupNotification extends Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "jhi_type")
    private GroupNotificationType type;

    public GroupNotification() {
    }

    public GroupNotification(String text, User author, Course course, GroupNotificationType type) {
        this.setText(text);
        this.setNotificationDate(ZonedDateTime.now());
        this.setAuthor(author);
        this.setCourse(course);
        this.setType(type);
    }

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
    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here, do not remove

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GroupNotification groupNotification = (GroupNotification) o;
        if (groupNotification.getId() == null || getId() == null) {
            return false;
        }
        return Objects.equals(getId(), groupNotification.getId());
    }

    public String getTopic() {
        return "/topic/course/" + getCourse().getId() + "/" + getType();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "GroupNotification{" + "id=" + getId() + ", type='" + getType() + "'" + "}";
    }
}
