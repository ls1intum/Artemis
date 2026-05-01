package de.tum.cit.aet.artemis.proof.domain;

import static de.tum.cit.aet.artemis.exercise.domain.ExerciseType.PROOF;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * A ProofExercise.
 */
@Entity
@DiscriminatorValue(value = "R")
@SecondaryTable(name = "proof_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProofExercise extends Exercise {

    @Column(table = "proof_exercise_details", name = "description")
    private String description;

    @Column(table = "proof_exercise_details", name = "predefined_checkbox_state")
    private Boolean predefinedCheckboxState = false;

    @Column(name = "example_solution")
    private String exampleSolution;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean isPredefinedCheckboxState() {
        return predefinedCheckboxState;
    }

    public void setPredefinedCheckboxState(Boolean predefinedCheckboxState) {
        this.predefinedCheckboxState = predefinedCheckboxState;
    }

    public String getExampleSolution() {
        return exampleSolution;
    }

    public void setExampleSolution(String exampleSolution) {
        this.exampleSolution = exampleSolution;
    }

    @Override
    public String getType() {
        return "proof";
    }

    @Override
    public ExerciseType getExerciseType() {
        return PROOF;
    }

    @Override
    public String toString() {
        return "ProofExercise{" + "id=" + getId() + "}";
    }
}
