package de.tum.cit.aet.artemis.course_notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Entity class for Course Notification Parameters.
 */
@Entity
@Table(name = "course_notification_parameter")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseNotificationParameter extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_notification_id", nullable = false)
    private CourseNotification courseNotification;

    @Column(name = "key", length = 20, nullable = false)
    private String key;

    @Column(name = "value", length = 100, nullable = false)
    private String value;

    /**
     * Default constructor.
     */
    public CourseNotificationParameter() {
    }

    /**
     * Constructor with all fields except id.
     *
     * @param courseNotification the course notification this parameter belongs to
     * @param key                the parameter key
     * @param value              the parameter value
     */
    public CourseNotificationParameter(CourseNotification courseNotification, String key, String value) {
        this.courseNotification = courseNotification;
        this.key = key;
        this.value = value;
    }

    /**
     * Gets the course notification this parameter belongs to.
     *
     * @return the course notification
     */
    public CourseNotification getCourseNotification() {
        return courseNotification;
    }

    /**
     * Sets the course notification this parameter belongs to.
     *
     * @param courseNotification the course notification to set
     */
    public void setCourseNotification(CourseNotification courseNotification) {
        this.courseNotification = courseNotification;
    }

    /**
     * Gets the parameter key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the parameter key.
     *
     * @param key the key to set
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Gets the parameter value.
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the parameter value.
     *
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "CourseNotificationParameter{" + "id=" + getId() + ", key='" + key + '\'' + ", value='" + value + '\'' + '}';
    }
}
