package de.tum.cit.aet.artemis.course_notification.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Represents the entity class for tracking a user's course notification status. Note that we can only track status like
 * "seen" and "archived" if the client supports them. For example, e-mail notifications cannot be marked as "archived"
 * as soon as the e-mail is deleted because there is no feedback mechanism from the e-mail client to artemis.
 */
@Entity
@Table(name = "user_course_notification_status")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@IdClass(UserCourseNotificationStatus.UserCourseNotificationStatusId.class)
public class UserCourseNotificationStatus implements Serializable {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_notification_id", nullable = false)
    private CourseNotification courseNotification;

    @Id
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

    /**
     * Checks if this notification status is equal to another object.
     * Two notification statuses are considered equal if they have the same course notification ID and user ID.
     *
     * @param other the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        UserCourseNotificationStatus that = (UserCourseNotificationStatus) other;
        return Objects.equals(courseNotification.getId(), that.courseNotification.getId()) && Objects.equals(user.getId(), that.user.getId());
    }

    /**
     * Generates a hash code for this notification status.
     * The hash code is based on the course notification ID and user ID.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int somePrimeNumber = 31;
        int result = 17;
        result = somePrimeNumber * result + (courseNotification != null ? Objects.hash(courseNotification.getId()) : 0);
        result = somePrimeNumber * result + (user != null ? Objects.hash(user.getId()) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "UserCourseNotificationStatus{" + "courseNotificationId=" + (courseNotification != null ? courseNotification.getId() : null) + ", userId="
                + (user != null ? user.getId() : null) + ", status=" + (status != null ? status.toString() : null) + '}';
    }

    /**
     * Class representing the composite primary key for UserCourseNotificationStatus.
     * This class combines courseNotification ID and user ID to form a composite key.
     */
    public static class UserCourseNotificationStatusId implements Serializable {

        private Long courseNotification;

        private Long user;

        /**
         * Default constructor required by JPA.
         */
        public UserCourseNotificationStatusId() {
        }

        /**
         * Constructs a composite key with the specified course notification ID and user ID.
         *
         * @param courseNotification the course notification ID
         * @param user               the user ID
         */
        public UserCourseNotificationStatusId(Long courseNotification, Long user) {
            this.courseNotification = courseNotification;
            this.user = user;
        }

        /**
         * Checks if this composite key is equal to another object.
         * Two keys are considered equal if they have the same course notification ID and user ID.
         *
         * @param other the object to compare with
         * @return true if the objects are equal, false otherwise
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            UserCourseNotificationStatusId that = (UserCourseNotificationStatusId) other;
            return Objects.equals(courseNotification, that.courseNotification) && Objects.equals(user, that.user);
        }

        /**
         * Generates a hash code for this composite key.
         * The hash code is based on the course notification ID and user ID.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            int somePrimeNumber = 31;
            int result = 17;
            result = somePrimeNumber * result + (courseNotification != null ? Objects.hash(courseNotification) : 0);
            result = somePrimeNumber * result + (user != null ? Objects.hash(user) : 0);
            return result;
        }
    }
}
