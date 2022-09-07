package de.tum.in.www1.artemis.domain.lecture;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.Hibernate;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningGoal;

@Entity
@DiscriminatorValue("E")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseUnit extends LectureUnit {

    // Note: Name, release date and learning goals will always be taken from associated exercise
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exercise_id")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private Exercise exercise;

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public boolean isVisibleToStudents() {
        return exercise == null || exercise.isVisibleToStudents();
    }

    @Override
    public String getName() {
        return exercise == null ? null : exercise.getTitle();
    }

    @Override
    public void setName(String name) {
        // Should be set in associated exercise
    }

    @Override
    public ZonedDateTime getReleaseDate() {
        return exercise == null ? null : exercise.getReleaseDate();
    }

    @Override
    public void setReleaseDate(ZonedDateTime releaseDate) {
        // Should be set in associated exercise
    }

    @Override
    public Set<LearningGoal> getLearningGoals() {
        return exercise == null || !Hibernate.isPropertyInitialized(exercise, "learningGoals") ? new HashSet<>() : exercise.getLearningGoals();
    }

    @Override
    public void setLearningGoals(Set<LearningGoal> learningGoals) {
        // Should be set in associated exercise
    }
}
