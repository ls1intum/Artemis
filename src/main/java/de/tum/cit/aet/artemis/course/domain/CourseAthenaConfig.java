package de.tum.cit.aet.artemis.course.domain;

import java.io.Serial;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "course_athena_config")
public class CourseAthenaConfig extends DomainObject {

    @Serial
    private static final long serialVersionUID = 1L;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", unique = true, nullable = false)
    @JsonIgnore
    private Course course;

    @Column(name = "formative_enabled", nullable = false)
    private boolean formativeEnabled = false;

    @Column(name = "grading_enabled", nullable = false)
    private boolean gradingEnabled = false;

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public boolean isFormativeEnabled() {
        return formativeEnabled;
    }

    public void setFormativeEnabled(boolean formativeEnabled) {
        this.formativeEnabled = formativeEnabled;
    }

    public boolean isGradingEnabled() {
        return gradingEnabled;
    }

    public void setGradingEnabled(boolean gradingEnabled) {
        this.gradingEnabled = gradingEnabled;
    }
}
