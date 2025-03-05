package de.tum.cit.aet.artemis.coursenotification.domain;

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
 * Entity class for User Course Notification Setting Preset.
 */
@Entity
@Table(name = "user_course_notification_setting_preset")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@IdClass(UserCourseNotificationSettingPreset.UserCourseNotificationSettingPresetId.class)
public class UserCourseNotificationSettingPreset implements Serializable {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "setting_preset", nullable = false)
    private Short settingPreset;

    /**
     * Default constructor.
     */
    public UserCourseNotificationSettingPreset() {
    }

    /**
     * Constructor with all fields except id.
     *
     * @param user          the user this setting preset belongs to
     * @param course        the course this setting preset is for
     * @param settingPreset the notification setting preset value
     */
    public UserCourseNotificationSettingPreset(User user, Course course, Short settingPreset) {
        this.user = user;
        this.course = course;
        this.settingPreset = settingPreset;
    }

    /**
     * Gets the user this setting preset belongs to.
     *
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * Sets the user this setting preset belongs to.
     *
     * @param user the user to set
     */
    public void setUser(User user) {
        this.user = user;
    }

    /**
     * Gets the course this setting preset is for.
     *
     * @return the course
     */
    public Course getCourse() {
        return course;
    }

    /**
     * Sets the course this setting preset is for.
     *
     * @param course the course to set
     */
    public void setCourse(Course course) {
        this.course = course;
    }

    /**
     * Gets the notification setting preset value.
     *
     * @return the setting preset
     */
    public Short getSettingPreset() {
        return settingPreset;
    }

    /**
     * Sets the notification setting preset value.
     *
     * @param settingPreset the setting preset to set
     */
    public void setSettingPreset(Short settingPreset) {
        this.settingPreset = settingPreset;
    }

    /**
     * Checks if this setting preset is equal to another object.
     * Two settings are considered equal if they have the same user ID and course ID.
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
        UserCourseNotificationSettingPreset that = (UserCourseNotificationSettingPreset) other;
        return Objects.equals(user.getId(), that.user.getId()) && Objects.equals(course.getId(), that.course.getId());
    }

    /**
     * Generates a hash code for this setting preset.
     * The hash code is based on the user ID and course ID.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        int somePrimeNumber = 41;
        int result = 23;
        result = somePrimeNumber * result + (user != null ? Objects.hash(user.getId()) : 0);
        result = somePrimeNumber * result + (course != null ? Objects.hash(course.getId()) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "UserCourseNotificationSettingPreset{" + "userId=" + (user != null ? user.getId() : null) + ", courseId=" + (course != null ? course.getId() : null)
                + ", settingPreset=" + settingPreset + '}';
    }

    /**
     * Class representing the composite primary key for UserCourseNotificationSettingPreset.
     * This class combines user ID and course ID to form a composite key.
     */
    public static class UserCourseNotificationSettingPresetId implements Serializable {

        private Long user;

        private Long course;

        /**
         * Default constructor required by JPA.
         */
        public UserCourseNotificationSettingPresetId() {
        }

        /**
         * Constructs a composite key with the specified user ID and course ID.
         *
         * @param user   the user ID
         * @param course the course ID
         */
        public UserCourseNotificationSettingPresetId(Long user, Long course) {
            this.user = user;
            this.course = course;
        }

        /**
         * Checks if this composite key is equal to another object.
         * Two keys are considered equal if they have the same user ID and course ID.
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
            UserCourseNotificationSettingPresetId that = (UserCourseNotificationSettingPresetId) other;
            return Objects.equals(user, that.user) && Objects.equals(course, that.course);
        }

        /**
         * Generates a hash code for this composite key.
         * The hash code is based on the user ID and course ID.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            int somePrimeNumber = 41;
            int result = 23;
            result = somePrimeNumber * result + (user != null ? Objects.hash(user) : 0);
            result = somePrimeNumber * result + (course != null ? Objects.hash(course) : 0);
            return result;
        }
    }
}
