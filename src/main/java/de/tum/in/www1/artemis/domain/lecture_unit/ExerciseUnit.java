package de.tum.in.www1.artemis.domain.lecture_unit;

import java.time.ZonedDateTime;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import de.tum.in.www1.artemis.domain.Exercise;

@Entity
@DiscriminatorValue("E")
public class ExerciseUnit extends LectureUnit {

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Override
    public String getName() {
        if (this.exercise != null && this.exercise.getTitle() != null) {
            return this.exercise.getTitle();
        }
        else {
            return null;
        }
    }

    @Override
    public void setName(String name) {
        // Do nothing as the name will always be taken from the exercise
    }

    @Override
    public ZonedDateTime getReleaseDate() {
        if (this.exercise != null && this.exercise.getReleaseDate() != null) {
            return this.exercise.getReleaseDate();
        }
        else {
            return null;
        }
    }

    @Override
    public void setReleaseDate(ZonedDateTime releaseDate) {
        // Do nothing as the release date will always be taken from the exercise
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public boolean isVisibleToStudents() {
        if (exercise == null) {
            return true;
        }
        else {
            return exercise.isVisibleToStudents();
        }
    }
}
