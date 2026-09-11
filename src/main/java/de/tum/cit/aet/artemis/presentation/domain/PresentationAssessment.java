package de.tum.cit.aet.artemis.presentation.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * A course-level presentation assessment.
 */
@Entity
@Table(name = "presentation_assessment")
public class PresentationAssessment extends DomainObject {

    public static final String ENTITY_NAME = "presentationAssessment";

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "max_points", nullable = false)
    private double maxPoints;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "presentationAssessments", allowSetters = true)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    private Exercise exercise;

    @OneToMany(mappedBy = "presentationAssessment", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PresentationAssessmentInstance> instances = new HashSet<>();

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

    public double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(double maxPoints) {
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

    public Set<PresentationAssessmentInstance> getInstances() {
        return instances;
    }

    public void setInstances(Set<PresentationAssessmentInstance> instances) {
        this.instances = instances;
    }

}
