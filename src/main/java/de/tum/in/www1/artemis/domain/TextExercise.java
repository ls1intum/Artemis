package de.tum.in.www1.artemis.domain;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.TEXT;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * A TextExercise.
 */
@Entity
@DiscriminatorValue(value = "T")
@SecondaryTable(name = "text_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextExercise extends Exercise {

    // used to distinguish the type when used in collections (e.g. SearchResultPageDTO --> resultsOnPage)
    public String getType() {
        return "text";
    }

    @Column(name = "example_solution")
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

    /**
     * set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        if (!isExampleSolutionPublished()) {
            setExampleSolution(null);
        }
        super.filterSensitiveInformation();
    }

    @Override
    public ExerciseType getExerciseType() {
        return TEXT;
    }

    @Override
    public String toString() {
        return "TextExercise{" + "id=" + getId() + ", exampleSolution='" + getExampleSolution() + "'" + "}";
    }

}
