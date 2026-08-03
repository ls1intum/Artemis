package de.tum.cit.aet.artemis.presentation.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.course.domain.Course;

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

    @Column(name = "result_points")
    private Double resultPoints;

    @Column(name = "presentation_date")
    private ZonedDateTime presentationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = "presentationAssessments", allowSetters = true)
    private Course course;

    @ManyToMany
    @JoinTable(name = "presentation_assessment_student", joinColumns = @JoinColumn(name = "presentation_assessment_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "student_id", referencedColumnName = "id"))
    private Set<User> students = new HashSet<>();

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

    public Double getResultPoints() {
        return resultPoints;
    }

    public void setResultPoints(Double resultPoints) {
        this.resultPoints = resultPoints;
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

    public Set<User> getStudents() {
        return students;
    }

    public void setStudents(Set<User> students) {
        this.students = students;
    }
}
