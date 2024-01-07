package de.tum.in.www1.artemis.domain.math;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.MATH;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * A MathExercise.
 */
@Entity
@DiscriminatorValue(value = "MATH")
@SecondaryTable(name = "math_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathExercise extends Exercise {

    @Column(name = "solution", columnDefinition = "JSON", table = "math_exercise_details")
    private String solution;

    @Column(name = "worked_solution", columnDefinition = "LONGTEXT", table = "math_exercise_details")
    private String workedSolution;

    @Column(name = "hint", columnDefinition = "JSON", table = "math_exercise_details")
    private String hint;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", table = "math_exercise_details")
    private MathInputType inputType;

    @Column(name = "input_configuration", columnDefinition = "JSON", table = "math_exercise_details")
    private String inputConfiguration;

    public String getSolution() {
        return solution;
    }

    public void setSolution(String solution) {
        this.solution = solution;
    }

    public String getWorkedSolution() {
        return workedSolution;
    }

    public void setWorkedSolution(String workedSolution) {
        this.workedSolution = workedSolution;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public MathInputType getInputType() {
        return inputType;
    }

    public void setInputType(MathInputType inputType) {
        this.inputType = inputType;
    }

    public String getInputConfiguration() {
        return inputConfiguration;
    }

    public void setInputConfiguration(String inputConfiguration) {
        this.inputConfiguration = inputConfiguration;
    }

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
