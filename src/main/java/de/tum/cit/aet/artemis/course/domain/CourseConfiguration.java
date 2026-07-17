package de.tum.cit.aet.artemis.course.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Holds course-level configuration values that are only needed in specific flows and should not widen the (already large)
 * {@code course} table nor be loaded on every course fetch. The association from {@link Course} is lazy, so this entity is
 * only materialized when explicitly accessed (e.g. the course settings form or the data-privacy cleanup logic).
 * <p>
 * Currently stores the grade-relevance flag that drives the GDPR retention period for a course's student data
 * (grade-relevant courses are retained longer than non-grade-relevant ones).
 */
@Entity
@Table(name = "course_configuration")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseConfiguration extends DomainObject {

    public static final String ENTITY_NAME = "courseConfiguration";

    @OneToOne(mappedBy = "courseConfiguration", fetch = FetchType.LAZY)
    @JsonIgnore
    private Course course;

    /**
     * Whether the course is grade-relevant (e.g. its results count towards official grades / exam records). Grade-relevant
     * courses have a longer legal data-retention period than non-grade-relevant ones. Defaults to {@code true} so that
     * courses are treated as grade-relevant unless an instructor explicitly opts out.
     */
    @Column(name = "grade_relevant", nullable = false)
    private boolean gradeRelevant = true;

    /**
     * When the data-privacy cleanup sent the instructors the "student data will be deleted after the grace period"
     * warning (and archived the course). It stays {@code null} until the course has actually been warned. The reset phase
     * only deletes student data of courses whose {@code resetWarningSentDate + grace} has elapsed, so this anchors the
     * grace period to the real warning event (not to the course end date) and guarantees a course is never reset before
     * its instructors were warned.
     */
    @Column(name = "reset_warning_sent_date")
    private ZonedDateTime resetWarningSentDate;

    /**
     * When the data-privacy cleanup actually reset (deleted) the course's student data. Stays {@code null} until the
     * reset has run. It makes the retention lifecycle one-shot (not warned → warned → reset), so an already-reset course
     * is excluded from both the warn and reset phases and enabled cron jobs never re-warn or re-reset an emptied course.
     */
    @Column(name = "student_data_reset_date")
    private ZonedDateTime studentDataResetDate;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public boolean isGradeRelevant() {
        return gradeRelevant;
    }

    public void setGradeRelevant(boolean gradeRelevant) {
        this.gradeRelevant = gradeRelevant;
    }

    public ZonedDateTime getResetWarningSentDate() {
        return resetWarningSentDate;
    }

    public void setResetWarningSentDate(ZonedDateTime resetWarningSentDate) {
        this.resetWarningSentDate = resetWarningSentDate;
    }

    public ZonedDateTime getStudentDataResetDate() {
        return studentDataResetDate;
    }

    public void setStudentDataResetDate(ZonedDateTime studentDataResetDate) {
        this.studentDataResetDate = studentDataResetDate;
    }
}
