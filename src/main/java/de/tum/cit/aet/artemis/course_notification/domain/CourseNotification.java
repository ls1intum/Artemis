package de.tum.cit.aet.artemis.course_notification.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Entity class for Course Notifications.
 */
@Entity
@Table(name = "course_notification")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseNotification extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "type", nullable = false)
    private Short type;

    @Column(name = "creation_date", nullable = false)
    private ZonedDateTime creationDate;

    @Column(name = "deletion_date")
    private ZonedDateTime deletionDate;

    @OneToMany(mappedBy = "courseNotification", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CourseNotificationParameter> parameters = new HashSet<>();

    @OneToMany(mappedBy = "courseNotification", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserCourseNotificationStatus> userStatuses = new HashSet<>();

    /**
     * Default constructor.
     */
    public CourseNotification() {
    }

    /**
     * Constructor with all fields except id.
     *
     * @param course       the course associated with this notification
     * @param type         the type index of notification
     * @param creationDate the date when this notification was created
     * @param deletionDate the date when this notification will be deleted
     */
    public CourseNotification(Course course, Short type, ZonedDateTime creationDate, ZonedDateTime deletionDate) {
        this.course = course;
        this.type = type;
        this.creationDate = creationDate;
        this.deletionDate = deletionDate;
    }

    /**
     * Gets the course associated with this notification.
     *
     * @return the course
     */
    public Course getCourse() {
        return course;
    }

    /**
     * Sets the course associated with this notification.
     *
     * @param course the course to set
     */
    public void setCourse(Course course) {
        this.course = course;
    }

    /**
     * Gets the notification type index.
     *
     * @return the notification type
     */
    public Short getType() {
        return type;
    }

    /**
     * Sets the notification type index.
     *
     * @param type the notification type to set
     */
    public void setType(Short type) {
        this.type = type;
    }

    /**
     * Gets the creation date of this notification.
     *
     * @return the creation date
     */
    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date of this notification.
     *
     * @param creationDate the creation date to set
     */
    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Gets the deletion date of this notification.
     *
     * @return the deletion date
     */
    public ZonedDateTime getDeletionDate() {
        return deletionDate;
    }

    /**
     * Sets the deletion date of this notification.
     *
     * @param deletionDate the deletion date to set
     */
    public void setDeletionDate(ZonedDateTime deletionDate) {
        this.deletionDate = deletionDate;
    }

    /**
     * Gets the parameters associated with this notification.
     *
     * @return the set of parameters
     */
    public Set<CourseNotificationParameter> getParameters() {
        return parameters;
    }

    /**
     * Sets the parameters associated with this notification.
     *
     * @param parameters the set of parameters to set
     */
    public void setParameters(Set<CourseNotificationParameter> parameters) {
        this.parameters = parameters;
    }

    /**
     * Gets the user statuses associated with this notification.
     *
     * @return the set of user statuses
     */
    public Set<UserCourseNotificationStatus> getUserStatuses() {
        return userStatuses;
    }

    /**
     * Sets the user statuses associated with this notification.
     *
     * @param userStatuses the set of user statuses to set
     */
    public void setUserStatuses(Set<UserCourseNotificationStatus> userStatuses) {
        this.userStatuses = userStatuses;
    }

    @Override
    public String toString() {
        return "CourseNotification{" + "id=" + getId() + ", type=" + type + ", creationDate=" + creationDate + ", deletionDate=" + deletionDate + '}';
    }
}
