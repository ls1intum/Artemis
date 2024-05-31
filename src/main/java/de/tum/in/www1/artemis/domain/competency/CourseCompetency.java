package de.tum.in.www1.artemis.domain.competency;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.Course;

/**
 * CourseCompetency is an abstract class for all competency types that are part of a course.
 * It is extended by {@link Competency} and {@link Prerequisite}
 */
@Entity
@Table(name = "course_competency")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public abstract class CourseCompetency extends BaseCompetency {

    @JsonIgnore
    public static final int DEFAULT_MASTERY_THRESHOLD = 100;

    @JsonIgnore
    public static final int MAX_TITLE_LENGTH = 255;

    @Column(name = "soft_due_date")
    private ZonedDateTime softDueDate;

    @Column(name = "mastery_threshold")
    private int masteryThreshold;

    @Column(name = "optional")
    private boolean optional;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    private Course course;

    @ManyToOne
    @JoinColumn(name = "linked_course_competency_id")
    @JsonIgnoreProperties({ "competencies" })
    private CourseCompetency linkedCourseCompetency;

    public CourseCompetency() {
    }

    public CourseCompetency(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, taxonomy);
        this.softDueDate = softDueDate;
        this.masteryThreshold = masteryThreshold;
        this.optional = optional;
    }

    public ZonedDateTime getSoftDueDate() {
        return softDueDate;
    }

    public void setSoftDueDate(ZonedDateTime softDueDate) {
        this.softDueDate = softDueDate;
    }

    public int getMasteryThreshold() {
        return masteryThreshold;
    }

    public void setMasteryThreshold(int masteryThreshold) {
        this.masteryThreshold = masteryThreshold;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public CourseCompetency getLinkedCourseCompetency() {
        return linkedCourseCompetency;
    }

    public void setLinkedCourseCompetency(CourseCompetency linkedCourseCompetency) {
        this.linkedCourseCompetency = linkedCourseCompetency;
    }
}
