package de.tum.in.www1.artemis.domain.competency;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

@Entity
@DiscriminatorValue("COMPETENCY")
public class Competency extends AbstractCompetency {

    // TODO: move to AbstractCompetency
    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties({ "competencies", "course" })
    private Set<Exercise> exercises = new HashSet<>();

    // TODO: move to AbstractCompetency
    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties("competencies")
    private Set<LectureUnit> lectureUnits = new HashSet<>();

    /**
     * A set of courses for which this competency is a prerequisite for.
     * TODO: remove this once the prerequisite migration is complete
     */
    @ManyToMany
    @JoinTable(name = "competency_course", joinColumns = @JoinColumn(name = "competency_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "course_id", referencedColumnName = "id"))
    @JsonIgnoreProperties({ "competencies", "prerequisites" })
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Set<Course> consecutiveCourses = new HashSet<>();

    public Competency() {
    }

    public Competency(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, softDueDate, masteryThreshold, taxonomy, optional);
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

    public void removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        exercise.getCompetencies().remove(this);
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

    public void setConsecutiveCourses(Set<Course> consecutiveCourses) {
        this.consecutiveCourses = consecutiveCourses;
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
