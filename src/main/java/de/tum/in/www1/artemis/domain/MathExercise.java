package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.MATH;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * A MathExercise.
 */
@Entity
@DiscriminatorValue(value = "MATH")
@SecondaryTable(name = "math_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathExercise extends Exercise {

    public String getType() {
        return "math";
    }

    @Column(name = "example_solution", table = "math_exercise_details")
    private String exampleSolution;

    public String getExampleSolution() {
        return exampleSolution;
    }

    public void setExampleSolution(String exampleSolution) {
        this.exampleSolution = exampleSolution;
    }

    @JsonIgnore
    public boolean isFeedbackSuggestionsEnabled() {
        return getAssessmentType() == AssessmentType.SEMI_AUTOMATIC;
    }

    /**
     * Disable feedback suggestions for this exercise by setting the assessment type to MANUAL.
     * Only changes the assessment type if feedback suggestions are currently enabled.
     */
    public void disableFeedbackSuggestions() {
        if (isFeedbackSuggestionsEnabled()) {
            setAssessmentType(AssessmentType.MANUAL);
        }
    }

    @Override
    public ExerciseType getExerciseType() {
        return MATH;
    }

    @Override
    public String toString() {
        return "MathExercise{" + "id=" + getId() + ", exampleSolution='" + getExampleSolution() + "'" + "}";
    }

}
