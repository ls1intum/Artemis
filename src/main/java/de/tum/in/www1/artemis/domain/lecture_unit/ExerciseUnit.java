package de.tum.in.www1.artemis.domain.lecture_unit;

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

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }
}
