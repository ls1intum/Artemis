package de.tum.cit.aet.artemis.course_notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Entity class for User Course Notification Status.
 */
@Entity
@Table(name = "user_course_notification_status")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserCourseNotificationStatus extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_notification_id", nullable = false)
    private CourseNotification courseNotification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated
    @Column(name = "status", nullable = false)
    private UserCourseNotificationStatusType status;

    /**
     * Default constructor.
     */
    public UserCourseNotificationStatus() {
    }

    /**
     * Constructor with all fields except id.
     *
     * @param courseNotification the course notification this status belongs to
     * @param user               the user this status is for
     * @param status             the notification status
     */
    public UserCourseNotificationStatus(CourseNotification courseNotification, User user, UserCourseNotificationStatusType status) {
        this.courseNotification = courseNotification;
        this.user = user;
        this.status = status;
    }

    /**
     * Gets the course notification this status belongs to.
     *
     * @return the course notification
     */
    public CourseNotification getCourseNotification() {
        return courseNotification;
    }

    /**
     * Sets the course notification this status belongs to.
     *
     * @param courseNotification the course notification to set
     */
    public void setCourseNotification(CourseNotification courseNotification) {
        this.courseNotification = courseNotification;
    }

    /**
     * Gets the user this status is for.
     *
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the user this status is for.
     *
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Gets the notification status.
     *
     * @return the status
     */
    public UserCourseNotificationStatusType getStatus() {
        return status;
    }

    /**
     * Sets the notification status.
     *
     * @param status the status to set
     */
    public void setStatus(UserCourseNotificationStatusType status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "UserCourseNotificationStatus{" + "id=" + getId() + ", status=" + status.toString() + '}';
    }
}
