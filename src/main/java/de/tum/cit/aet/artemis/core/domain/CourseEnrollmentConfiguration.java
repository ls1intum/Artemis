package de.tum.cit.aet.artemis.core.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * Configuration entity for course enrollment settings.
 * <p>
 * This entity is lazily loaded from the Course entity to reduce database load
 * when courses are frequently fetched but enrollment settings are rarely needed.
 */
@Entity
@Table(name = "course_enrollment_configuration")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseEnrollmentConfiguration extends DomainObject {

    @OneToOne(mappedBy = "enrollmentConfiguration")
    @JsonIgnore
    private Course course;

    @Column(name = "enrollment_enabled")
    private Boolean enrollmentEnabled;

    @Column(name = "enrollment_start_date")
    private ZonedDateTime enrollmentStartDate;

    @Column(name = "enrollment_end_date")
    private ZonedDateTime enrollmentEndDate;

    @Column(name = "enrollment_confirmation_message", length = 2000)
    private String enrollmentConfirmationMessage;

    @Column(name = "unenrollment_enabled", nullable = false)
    private boolean unenrollmentEnabled = false;

    @Column(name = "unenrollment_end_date")
    private ZonedDateTime unenrollmentEndDate;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Boolean isEnrollmentEnabled() {
        return enrollmentEnabled;
    }

    public void setEnrollmentEnabled(Boolean enrollmentEnabled) {
        this.enrollmentEnabled = enrollmentEnabled;
    }

    public ZonedDateTime getEnrollmentStartDate() {
        return enrollmentStartDate;
    }

    public void setEnrollmentStartDate(ZonedDateTime enrollmentStartDate) {
        this.enrollmentStartDate = enrollmentStartDate;
    }

    public ZonedDateTime getEnrollmentEndDate() {
        return enrollmentEndDate;
    }

    public void setEnrollmentEndDate(ZonedDateTime enrollmentEndDate) {
        this.enrollmentEndDate = enrollmentEndDate;
    }

    public String getEnrollmentConfirmationMessage() {
        return enrollmentConfirmationMessage;
    }

    public void setEnrollmentConfirmationMessage(String enrollmentConfirmationMessage) {
        this.enrollmentConfirmationMessage = enrollmentConfirmationMessage;
    }

    public boolean isUnenrollmentEnabled() {
        return unenrollmentEnabled;
    }

    public void setUnenrollmentEnabled(boolean unenrollmentEnabled) {
        this.unenrollmentEnabled = unenrollmentEnabled;
    }

    public ZonedDateTime getUnenrollmentEndDate() {
        return unenrollmentEndDate;
    }

    public void setUnenrollmentEndDate(ZonedDateTime unenrollmentEndDate) {
        this.unenrollmentEndDate = unenrollmentEndDate;
    }

    /**
     * Determine whether the current date is within the enrollment period (after start, before end).
     *
     * @return true if the current date is within the enrollment period, false otherwise
     */
    @JsonIgnore
    public boolean enrollmentIsActive() {
        ZonedDateTime now = ZonedDateTime.now();
        return (enrollmentStartDate == null || enrollmentStartDate.isBefore(now)) && (enrollmentEndDate == null || enrollmentEndDate.isAfter(now));
    }

    /**
     * Determine whether the current date is within the unenrollment period (after start, before end).
     * <p>
     * The unenrollment period starts with the enrollment start date and ends with the unenrollment end date if present,
     * otherwise the course end date will be used as the end of the period.
     *
     * @param courseEndDate the end date of the course (used as fallback if unenrollment end date is not set)
     * @return true if the current date is within the unenrollment period, false otherwise
     */
    @JsonIgnore
    public boolean unenrollmentIsActive(ZonedDateTime courseEndDate) {
        ZonedDateTime now = ZonedDateTime.now();
        final boolean startCondition = enrollmentStartDate == null || enrollmentStartDate.isBefore(now);
        final boolean endCondition = (unenrollmentEndDate == null && courseEndDate == null) || (unenrollmentEndDate == null && courseEndDate.isAfter(now))
                || (unenrollmentEndDate != null && unenrollmentEndDate.isAfter(now));
        return startCondition && endCondition;
    }

    /**
     * Validates if the start and end date to enroll in the course fulfill all requirements.
     *
     * @param courseStartDate the start date of the course
     * @param courseEndDate   the end date of the course
     * @throws BadRequestAlertException if the enrollment period is invalid
     */
    public void validateEnrollmentStartAndEndDate(ZonedDateTime courseStartDate, ZonedDateTime courseEndDate) {
        if (enrollmentStartDate == null || enrollmentEndDate == null) {
            return;
        }
        final String errorKey = "enrollmentPeriodInvalid";
        if (!enrollmentStartDate.isBefore(enrollmentEndDate)) {
            throw new BadRequestAlertException("Enrollment start date must be before the end date.", Course.ENTITY_NAME, errorKey, true);
        }

        if (courseStartDate == null || courseEndDate == null) {
            throw new BadRequestAlertException("Enrollment can not be set if the course has no assigned start and end date.", Course.ENTITY_NAME, errorKey, true);
        }

        if (enrollmentEndDate.isAfter(courseEndDate)) {
            throw new BadRequestAlertException("Enrollment end can not be after the end date of the course.", Course.ENTITY_NAME, errorKey, true);
        }
    }

    /**
     * Validates if the end date to unenroll from the course fulfills all requirements.
     *
     * @param courseEndDate the end date of the course
     * @throws BadRequestAlertException if the unenrollment end date is invalid
     */
    public void validateUnenrollmentEndDate(ZonedDateTime courseEndDate) {
        if (unenrollmentEndDate == null) {
            return;
        }

        final String errorKey = "unenrollmentEndDateInvalid";

        if (enrollmentStartDate == null || enrollmentEndDate == null) {
            throw new BadRequestAlertException("Unenrollment end date requires a configured enrollment period.", Course.ENTITY_NAME, errorKey, true);
        }

        if (!enrollmentEndDate.isBefore(unenrollmentEndDate)) {
            throw new BadRequestAlertException("End date for enrollment must be before the end date to unenroll.", Course.ENTITY_NAME, errorKey, true);
        }

        if (courseEndDate != null && unenrollmentEndDate.isAfter(courseEndDate)) {
            throw new BadRequestAlertException("End date for enrollment can not be after the end date of the course.", Course.ENTITY_NAME, errorKey, true);
        }
    }

    /**
     * Validates the enrollment confirmation message length.
     *
     * @throws BadRequestAlertException if the message is too long
     */
    public void validateEnrollmentConfirmationMessage() {
        if (enrollmentConfirmationMessage != null && enrollmentConfirmationMessage.length() > 2000) {
            throw new BadRequestAlertException("Confirmation enrollment message must be shorter than 2000 characters", Course.ENTITY_NAME, "confirmationEnrollmentMessageInvalid",
                    true);
        }
    }
}
