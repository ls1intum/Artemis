package de.tum.cit.aet.artemis.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

@Entity
@Table(name = "course_athena_config")
public class CourseAthenaConfig extends DomainObject {

    /**
     * The course this configuration belongs to. The key lives here rather than on the course: the course carries no
     * mapped association to its Athena configuration, so loading a course can never pull this row in, and the
     * configuration cannot outlive the course. Read it through {@code CourseAthenaConfigRepository} where it is needed.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, unique = true)
    @JsonIgnore
    @Parent
    private Course course;

    @Column(name = "grading_feedback_enabled", nullable = false)
    private boolean gradingFeedbackEnabled = false;

    @Column(name = "formative_feedback_enabled", nullable = false)
    private boolean formativeFeedbackEnabled = false;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public boolean isGradingFeedbackEnabled() {
        return gradingFeedbackEnabled;
    }

    public void setGradingFeedbackEnabled(boolean gradingFeedbackEnabled) {
        this.gradingFeedbackEnabled = gradingFeedbackEnabled;
    }

    public boolean isFormativeFeedbackEnabled() {
        return formativeFeedbackEnabled;
    }

    public void setFormativeFeedbackEnabled(boolean formativeFeedbackEnabled) {
        this.formativeFeedbackEnabled = formativeFeedbackEnabled;
    }
}
