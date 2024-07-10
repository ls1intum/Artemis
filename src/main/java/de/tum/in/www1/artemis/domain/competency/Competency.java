package de.tum.in.www1.artemis.domain.competency;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;

@Entity
@DiscriminatorValue("C")
public class Competency extends CourseCompetency {

    // TODO: move properties (linkedStandardizedCompetency, exercises, lectureUnits, userProgress, learningPaths) to CourseCompetency when refactoring
    @ManyToOne
    @JoinColumn(name = "linked_standardized_competency_id")
    @JsonIgnoreProperties({ "competencies" })
    private StandardizedCompetency linkedStandardizedCompetency;

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties({ "competencies", "course" })
    private Set<Exercise> exercises = new HashSet<>();

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties("competencies")
    private Set<LectureUnit> lectureUnits = new HashSet<>();

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties({ "user", "competency" })
    private Set<CompetencyProgress> userProgress = new HashSet<>();

    @ManyToMany(mappedBy = "competencies")
    @JsonIgnoreProperties({ "competencies", "course" })
    private Set<LearningPath> learningPaths = new HashSet<>();

    @OneToMany(mappedBy = "competency", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CompetencyJol> competencyJols = new HashSet<>();

    public Competency() {
    }

    public Competency(String title, String description, ZonedDateTime softDueDate, Integer masteryThreshold, CompetencyTaxonomy taxonomy, boolean optional) {
        super(title, description, softDueDate, masteryThreshold, taxonomy, optional);
    }

    public StandardizedCompetency getLinkedStandardizedCompetency() {
        return linkedStandardizedCompetency;
    }

    public void setLinkedStandardizedCompetency(StandardizedCompetency linkedStandardizedCompetency) {
        this.linkedStandardizedCompetency = linkedStandardizedCompetency;
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
     * Note: ExerciseUnits are not accepted, should be set via the connected exercise
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
        this.lectureUnits.removeIf(lectureUnit -> lectureUnit instanceof ExerciseUnit);
    }
}
