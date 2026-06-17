package de.tum.cit.aet.artemis.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "course_athena_config")
public class CourseAthenaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "course_id", nullable = false, unique = true)
    private Course course;

    @Column(name = "grading_feedback_enabled", nullable = false)
    private boolean gradingFeedbackEnabled = false;

    @Column(name = "formative_feedback_enabled", nullable = false)
    private boolean formativeFeedbackEnabled = false;

    public Long getId() {
        return id;
    }

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
