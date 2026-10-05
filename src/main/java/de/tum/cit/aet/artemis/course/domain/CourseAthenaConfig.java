package de.tum.cit.aet.artemis.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "course_athena_config")
public class CourseAthenaConfig extends DomainObject {

    @Transient
    @JsonIgnore
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
