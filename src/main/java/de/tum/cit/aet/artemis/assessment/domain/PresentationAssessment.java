package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * A course-level presentation assessment.
 */
@Entity
@Table(name = "presentation_assessment")
public class PresentationAssessment extends DomainObject {

    public static final String ENTITY_NAME = "presentationAssessment";

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_method", nullable = false, length = 20)
    private PresentationGradingMethod gradingMethod;

    @Column(name = "max_points")
    private Double maxPoints;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    @Parent
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    private Exercise exercise;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PresentationGradingMethod getGradingMethod() {
        return gradingMethod;
    }

    public void setGradingMethod(PresentationGradingMethod gradingMethod) {
        this.gradingMethod = gradingMethod;
    }

    public Double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Double maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public long getVersion() {
        return version;
    }

}
