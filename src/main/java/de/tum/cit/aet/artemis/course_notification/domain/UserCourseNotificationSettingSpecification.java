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
 * Entity class for User Course Notification Setting Specification.
 */
@Entity
@Table(name = "user_course_notification_setting_specification")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserCourseNotificationSettingSpecification extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

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

    @Override
    public String toString() {
        return "UserCourseNotificationSettingSpecification{" + "id=" + getId() + ", user=" + user.getId() + ", course=" + course.getId() + ", email=" + email + ", push=" + push
                + ", webapp=" + webapp + ", summary=" + summary + '}';
    }
}
