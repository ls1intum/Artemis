package de.tum.cit.aet.artemis.course_notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Entity class for User Course Notification Setting Preset.
 */
@Entity
@Table(name = "user_course_notification_setting_preset")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserCourseNotificationSettingPreset extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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

    @Override
    public String toString() {
        return "UserCourseNotificationSettingPreset{" + "id=" + getId() + ", settingPreset=" + settingPreset + '}';
    }
}
