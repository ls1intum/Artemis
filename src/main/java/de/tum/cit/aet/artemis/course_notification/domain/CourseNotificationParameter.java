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

/**
 * Entity class for Course Notification Parameters.
 */
@Entity
@Table(name = "course_notification_parameter")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@IdClass(CourseNotificationParameter.CourseNotificationParameterId.class)
public class CourseNotificationParameter implements Serializable {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_notification_id", nullable = false)
    private CourseNotification courseNotification;

    @Id
    @Column(name = "param_key", length = 20, nullable = false)
    private String paramKey;

    @Column(name = "param_value", length = 100, nullable = false)
    private String paramValue;

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
        this.paramKey = key;
        this.paramValue = value;
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
        return paramKey;
    }

    /**
     * Sets the parameter key.
     *
     * @param key the key to set
     */
    public void setKey(String key) {
        this.paramKey = key;
    }

    /**
     * Gets the parameter value.
     *
     * @return the value
     */
    public String getValue() {
        return paramValue;
    }

    /**
     * Sets the parameter value.
     *
     * @param value the value to set
     */
    public void setValue(String value) {
        this.paramValue = value;
    }

    /**
     * Checks if this parameter is equal to another object.
     * Two parameters are considered equal if they have the same course notification ID and key.
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
        CourseNotificationParameter that = (CourseNotificationParameter) o;
        return Objects.equals(courseNotification.getId(), that.courseNotification.getId()) && Objects.equals(paramKey, that.paramKey);
    }

    /**
     * Generates a hash code for this parameter.
     * The hash code is based on the course notification ID and key.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(courseNotification.getId(), paramKey);
    }

    /**
     * Returns a string representation of this parameter.
     *
     * @return a string representation
     */
    @Override
    public String toString() {
        return "CourseNotificationParameter{" + "courseNotificationId=" + (courseNotification != null ? courseNotification.getId() : null) + ", key='" + paramKey + '\''
                + ", value='" + paramValue + '\'' + '}';
    }

    /**
     * Class representing the composite primary key for CourseNotificationParameter.
     * This class combines courseNotification ID and key to form a composite key.
     */
    public static class CourseNotificationParameterId implements Serializable {

        private Long courseNotification;

        private String paramKey;

        /**
         * Default constructor required by JPA.
         */
        public CourseNotificationParameterId() {
        }

        /**
         * Constructs a composite key with the specified course notification ID and key.
         *
         * @param courseNotification the course notification ID
         * @param key                the parameter key
         */
        public CourseNotificationParameterId(Long courseNotification, String key) {
            this.courseNotification = courseNotification;
            this.paramKey = key;
        }

        /**
         * Checks if this composite key is equal to another object.
         * Two keys are considered equal if they have the same course notification ID and key.
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
            CourseNotificationParameterId that = (CourseNotificationParameterId) o;
            return Objects.equals(courseNotification, that.courseNotification) && Objects.equals(paramKey, that.paramKey);
        }

        /**
         * Generates a hash code for this composite key.
         * The hash code is based on the course notification ID and key.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(courseNotification, paramKey);
        }
    }
}
