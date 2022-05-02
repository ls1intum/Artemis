package de.tum.in.www1.artemis.domain.lecture;

import java.time.ZonedDateTime;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

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
}
