package de.tum.cit.aet.artemis.math.domain;

import static de.tum.cit.aet.artemis.exercise.domain.ExerciseType.MATH;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * A MathExercise.
 */
@Entity
@DiscriminatorValue(value = "R")
@SecondaryTable(name = "math_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathExercise extends Exercise {

    @Column(table = "math_exercise_details", name = "description")
    private String description;

    @Column(table = "math_exercise_details", name = "example_solution")
    private String exampleSolution;

    @Column(table = "math_exercise_details", name = "manual_derivation")
    private boolean manualDerivation = false;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExampleSolution() {
        return exampleSolution;
    }

    public void setExampleSolution(String exampleSolution) {
        this.exampleSolution = exampleSolution;
    }

    public boolean isManualDerivation() {
        return manualDerivation;
    }

    public void setManualDerivation(boolean manualDerivation) {
        this.manualDerivation = manualDerivation;
    }

    /**
     * Set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        if (!isExampleSolutionPublished()) {
            setExampleSolution(null);
        }
        super.filterSensitiveInformation();
    }

    @Override
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
