package de.tum.in.www1.artemis.domain.competency;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

@Entity
@Table(name = "competency")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Competency extends BaseCompetency {

    @JsonIgnore
    public static final int DEFAULT_MASTERY_THRESHOLD = 50;

    @Column(name = "soft_due_date")
    private ZonedDateTime softDueDate;

    @Column(name = "mastery_threshold")
    private Integer masteryThreshold;

    @Column(name = "optional")
    private boolean optional;

    @ManyToOne
    @JoinColumn(name = "course_id")
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    private Course course;

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties({ "competencies", "course" })
    private Set<Exercise> exercises = new HashSet<>();

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties("competencies")
    private Set<LectureUnit> lectureUnits = new HashSet<>();

    /**
     * A set of courses for which this competency is a prerequisite for.
     */
    @ManyToMany
    @JoinTable(name = "competency_course", joinColumns = @JoinColumn(name = "competency_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "course_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Course> consecutiveCourses = new HashSet<>();

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties({ "user", "competency" })
    private Set<CompetencyProgress> userProgress = new HashSet<>();

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties({ "competencies", "course" })
    private Set<LearningPath> learningPaths = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "linked_standardized_competency_id")
    @JsonIgnoreProperties({ "competencies" })
    private StandardizedCompetency linkedStandardizedCompetency;

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CompetencyJOL> competencyJOLs = new HashSet<>();

    public Competency() {
    }

    public Competency(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, taxonomy);
        this.softDueDate = softDueDate;
        this.masteryThreshold = masteryThreshold;
        this.optional = optional;
    }

    public ZonedDateTime getSoftDueDate() {
        return softDueDate;
    }

    public void setSoftDueDate(ZonedDateTime dueDate) {
        this.softDueDate = dueDate;
    }

    public int getMasteryThreshold() {
        return masteryThreshold == null ? 100 : this.masteryThreshold;
    }

    public void setMasteryThreshold(Integer masteryThreshold) {
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

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public void addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        exercise.getCompetencies().add(this);
    }

    public Set<LectureUnit> getLectureUnits() {
        return lectureUnits;
    }

    public void setLectureUnits(Set<LectureUnit> lectureUnits) {
        this.lectureUnits = lectureUnits;
    }

    /**
     * Adds the lecture unit to the competency (bidirectional)
     * Note: ExerciseUnits are not accepted, should be set via the connected exercise (see {@link #addExercise(Exercise)})
     *
     * @param lectureUnit The lecture unit to add
     */
    public void addLectureUnit(LectureUnit lectureUnit) {
        if (lectureUnit instanceof ExerciseUnit) {
            // The competencies of ExerciseUnits are taken from the corresponding exercise
            throw new IllegalArgumentException("ExerciseUnits can not be connected to competencies");
        }
        this.lectureUnits.add(lectureUnit);
        lectureUnit.getCompetencies().add(this);
    }

    /**
     * Removes the lecture unit from the competency (bidirectional)
     * Note: ExerciseUnits are not accepted, should be set via the connected exercise (see {@link #removeExercise(Exercise)})
     *
     * @param lectureUnit The lecture unit to remove
     */
    public void removeLectureUnit(LectureUnit lectureUnit) {
        if (lectureUnit instanceof ExerciseUnit) {
            // The competencies of ExerciseUnits are taken from the corresponding exercise
            throw new IllegalArgumentException("ExerciseUnits can not be disconnected from competencies");
        }
        this.lectureUnits.remove(lectureUnit);
        lectureUnit.getCompetencies().remove(this);
    }

    public Set<Course> getConsecutiveCourses() {
        return consecutiveCourses;
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

    public StandardizedCompetency getLinkedStandardizedCompetency() {
        return linkedStandardizedCompetency;
    }

    public void setLinkedStandardizedCompetency(StandardizedCompetency linkedStandardizedCompetency) {
        this.linkedStandardizedCompetency = linkedStandardizedCompetency;
    }

    /**
     * Ensure that exercise units are connected to competencies through the corresponding exercise
     */
    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        this.lectureUnits.removeIf(lectureUnit -> lectureUnit instanceof ExerciseUnit);
    }
}
