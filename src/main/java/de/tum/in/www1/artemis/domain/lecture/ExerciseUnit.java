package de.tum.in.www1.artemis.domain.lecture;

import javax.persistence.*;
import javax.persistence.Entity;

import org.hibernate.annotations.*;
import org.hibernate.annotations.Cache;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.artemis.domain.Exercise;

@Entity
@DiscriminatorValue("E")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseUnit extends LectureUnit {

    // Note: Name and Release Date will always be taken from associated exercise
    @ManyToOne
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
        if (exercise == null) {
            return true;
        }
        else {
            return exercise.isVisibleToStudents();
        }
    }
}
