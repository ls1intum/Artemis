package de.tum.cit.aet.artemis.atlas.domain.competency;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * CourseCompetency is an abstract class for all competency types that are part of a course.
 * It is extended by {@link Competency} and {@link Prerequisite}
 */
@Entity
@Table(name = "competency")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = Competency.class, name = "competency"),
    @JsonSubTypes.Type(value = Prerequisite.class, name = "prerequisite")
})
// @formatter:on
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
    @JoinColumn(name = "linked_standardized_competency_id")
    @JsonIgnoreProperties({ "competencies" })
    private StandardizedCompetency linkedStandardizedCompetency;

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("competency")
    private Set<CompetencyExerciseLink> exerciseLinks = new HashSet<>();

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("competency")
    private Set<CompetencyLectureUnitLink> lectureUnitLinks = new HashSet<>();

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties({ "user", "competency" })
    private Set<CompetencyProgress> userProgress = new HashSet<>();

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties({ "competencies", "course" })
    private Set<LearningPath> learningPaths = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    private Course course;

    @ManyToOne
    @JoinColumn(name = "linked_course_competency_id")
    @JsonIgnoreProperties({ "competencies" })
    private CourseCompetency linkedCourseCompetency;

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CompetencyJol> competencyJols = new HashSet<>();

    public abstract String getType();

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

    @ManyToOne
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

    public StandardizedCompetency getLinkedStandardizedCompetency() {
        return linkedStandardizedCompetency;
    }

    public void setLinkedStandardizedCompetency(StandardizedCompetency linkedStandardizedCompetency) {
        this.linkedStandardizedCompetency = linkedStandardizedCompetency;
    }

    public Set<CompetencyExerciseLink> getExerciseLinks() {
        return exerciseLinks;
    }

    public void setExerciseLinks(Set<CompetencyExerciseLink> exerciseLinks) {
        this.exerciseLinks = exerciseLinks;
    }

    public Set<CompetencyLectureUnitLink> getLectureUnitLinks() {
        return lectureUnitLinks;
    }

    public void setLectureUnitLinks(Set<CompetencyLectureUnitLink> lectureUnitLinks) {
        this.lectureUnitLinks = lectureUnitLinks;
    }

    /**
     * Removes the lecture unit from the competency (bidirectional)
     * Note: ExerciseUnits are not accepted, should be set via the connected exercise
     *
     * @param lectureUnit The lecture unit to remove
     */
    public void removeLectureUnit(LectureUnit lectureUnit) {
        if (lectureUnit instanceof ExerciseUnit) {
            // The competencies of ExerciseUnits are taken from the corresponding exercise
            throw new IllegalArgumentException("ExerciseUnits can not be disconnected from competencies");
        }
        this.lectureUnitLinks.remove(lectureUnit);
        lectureUnit.getCompetencyLinks().remove(this);
    }

    public Set<CompetencyProgress> getUserProgress() {
        return userProgress;
    }

    public void setUserProgress(Set<CompetencyProgress> userProgress) {
        this.userProgress = userProgress;
    }

    public Set<LearningPath> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(Set<LearningPath> learningPaths) {
        this.learningPaths = learningPaths;
    }

    /**
     * Ensure that exercise units are connected to competencies through the corresponding exercise
     */
    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        this.lectureUnitLinks.removeIf(lectureUnit -> lectureUnit.getLectureUnit() instanceof ExerciseUnit);
    }
}
