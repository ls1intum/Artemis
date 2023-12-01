package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.MATH;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * A MathExercise.
 */
@Entity
@DiscriminatorValue(value = "MATH")
// @SecondaryTable(name = "math_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathExercise extends TextExercise {

    public String getType() {
        return "math";
    }

    @Override
    public ExerciseType getExerciseType() {
        return MATH;
    }

    @Override
    public String toString() {
        return "MathExercise{" + "id=" + getId() + "}";
    }

}
