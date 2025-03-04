package de.tum.cit.aet.artemis.course_notification.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Entity class for User Course Notification Setting Specification.
 */
@Entity
@Table(name = "user_course_notification_setting_specification")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@IdClass(UserCourseNotificationSettingSpecification.UserCourseNotificationSettingSpecificationId.class)
public class UserCourseNotificationSettingSpecification implements Serializable {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Id
    @Column(name = "course_notification_type", nullable = false)
    private Short courseNotificationType;

    @Column(name = "email", nullable = false)
    private boolean email;

    @Column(name = "push", nullable = false)
    private boolean push;

    @Column(name = "webapp", nullable = false)
    private boolean webapp;

    @Column(name = "summary", nullable = false)
    private boolean summary;

    /**
     * Default constructor.
     */
    public UserCourseNotificationSettingSpecification() {
    }

    /**
     * Constructor with all fields except id.
     *
     * @param user    the user associated with the setting
     * @param course  the course associated with the setting
     * @param email   email notification setting
     * @param push    push notification setting
     * @param webapp  webapp notification setting
     * @param summary summary notification setting
     */
    public UserCourseNotificationSettingSpecification(User user, Course course, boolean email, boolean push, boolean webapp, boolean summary) {
        this.user = user;
        this.course = course;
        this.email = email;
        this.push = push;
        this.webapp = webapp;
        this.summary = summary;
    }

    /**
     * Gets the user associated with this setting.
     *
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the user associated with this setting.
     *
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Gets the course associated with this setting.
     *
     * @return the course
     */
    public Course getCourse() {
        return course;
    }

    /**
     * Sets the course associated with this setting.
     *
     * @param course the course to set
     */
    public void setCourse(Course course) {
        this.course = course;
    }

    /**
     * Gets the course notification type associated with this setting.
     *
     * @return the course notification type
     */
    public Short getCourseNotificationType() {
        return courseNotificationType;
    }

    /**
     * Sets the course notification type associated with this setting.
     *
     * @param courseNotificationType the course notification type to set
     */
    public void setCourseNotificationType(Short courseNotificationType) {
        this.courseNotificationType = courseNotificationType;
    }

    /**
     * Determines if email notifications are enabled.
     *
     * @return true if email notifications are enabled, otherwise false
     */
    public boolean isEmail() {
        return email;
    }

    /**
     * Sets the email notification setting.
     *
     * @param email the email setting to set
     */
    public void setEmail(boolean email) {
        this.email = email;
    }

    /**
     * Determines if push notifications are enabled.
     *
     * @return true if push notifications are enabled, otherwise false
     */
    public boolean isPush() {
        return push;
    }

    /**
     * Sets the push notification setting.
     *
     * @param push the push setting to set
     */
    public void setPush(boolean push) {
        this.push = push;
    }

    /**
     * Determines if webapp notifications are enabled.
     *
     * @return true if webapp notifications are enabled, otherwise false
     */
    public boolean isWebapp() {
        return webapp;
    }

    /**
     * Sets the webapp notification setting.
     *
     * @param webapp the webapp setting to set
     */
    public void setWebapp(boolean webapp) {
        this.webapp = webapp;
    }

    /**
     * Determines if summary notifications are enabled.
     *
     * @return true if summary notifications are enabled, otherwise false
     */
    public boolean isSummary() {
        return summary;
    }

    /**
     * Sets the summary notification setting.
     *
     * @param summary the summary setting to set
     */
    public void setSummary(boolean summary) {
        this.summary = summary;
    }

    /**
     * Checks if this setting specification is equal to another object.
     * Two setting specifications are considered equal if they have the same user ID, course ID, and course notification type.
     *
     * @param o the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UserCourseNotificationSettingSpecification that = (UserCourseNotificationSettingSpecification) o;
        return Objects.equals(user.getId(), that.user.getId()) && Objects.equals(course.getId(), that.course.getId())
                && Objects.equals(courseNotificationType, that.courseNotificationType);
    }

    /**
     * Generates a hash code for this setting specification.
     * The hash code is based on the user ID, course ID, and course notification type.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(user.getId(), course.getId(), courseNotificationType);
    }

    @Override
    public String toString() {
        return "UserCourseNotificationSettingSpecification{" + "userId=" + (user != null ? user.getId() : null) + ", courseId=" + (course != null ? course.getId() : null)
                + ", courseNotificationType=" + courseNotificationType + ", email=" + email + ", push=" + push + ", webapp=" + webapp + ", summary=" + summary + '}';
    }

    /**
     * Class representing the composite primary key for UserCourseNotificationSettingSpecification.
     * This class combines user ID, course ID, and course notification type to form a composite key.
     */
    public static class UserCourseNotificationSettingSpecificationId implements Serializable {

        private Long user;

        private Long course;

        private Short courseNotificationType;

        /**
         * Default constructor required by JPA.
         */
        public UserCourseNotificationSettingSpecificationId() {
        }

        /**
         * Constructs a composite key with the specified user ID, course ID, and course notification type.
         *
         * @param user                   the user ID
         * @param course                 the course ID
         * @param courseNotificationType the course notification type
         */
        public UserCourseNotificationSettingSpecificationId(Long user, Long course, Short courseNotificationType) {
            this.user = user;
            this.course = course;
            this.courseNotificationType = courseNotificationType;
        }

        /**
         * Checks if this composite key is equal to another object.
         * Two keys are considered equal if they have the same user ID, course ID, and course notification type.
         *
         * @param o the object to compare with
         * @return true if the objects are equal, false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            UserCourseNotificationSettingSpecificationId that = (UserCourseNotificationSettingSpecificationId) o;
            return Objects.equals(user, that.user) && Objects.equals(course, that.course) && Objects.equals(courseNotificationType, that.courseNotificationType);
        }

        /**
         * Generates a hash code for this composite key.
         * The hash code is based on the user ID, course ID, and course notification type.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(user, course, courseNotificationType);
        }
    }
}
