package de.tum.in.www1.artemis.domain;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A GroupNotification.
 */
@Entity
@DiscriminatorValue(value="G")
public class GroupNotification extends Notification implements Serializable {

    private static final long serialVersionUID = 1L;
    
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

    public void setCourse(Course course) {
        this.course = course;
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

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "GroupNotification{" +
            "id=" + getId() +
            ", type='" + getType() + "'" +
            "}";
    }
}
