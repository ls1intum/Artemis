package de.tum.cit.aet.artemis.presentation.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * A course-level presentation assessment.
 */
@Entity
@Table(name = "presentation_assessment")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PresentationAssessment extends DomainObject {

    public static final String ENTITY_NAME = "presentationAssessment";

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "max_points", nullable = false)
    private double maxPoints;

    @Column(name = "presentation_date")
    private ZonedDateTime presentationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "presentationAssessments", allowSetters = true)
    private Course course;

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

    public ZonedDateTime getPresentationDate() {
        return presentationDate;
    }

    public void setPresentationDate(ZonedDateTime presentationDate) {
        this.presentationDate = presentationDate;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }
}
