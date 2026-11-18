package de.tum.cit.aet.artemis.text.domain;

import static de.tum.cit.aet.artemis.exercise.domain.ExerciseType.TEXT;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseWithExampleSolution;

/**
 * A TextExercise.
 */
@Entity
@DiscriminatorValue(value = "T")
@SecondaryTable(name = "text_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextExercise extends ExerciseWithExampleSolution {

    // used to distinguish the type when used in collections (e.g. SearchResultPageDTO --> resultsOnPage)
    @Override
    public String getType() {
        return "text";
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
