package de.tum.cit.aet.artemis.athena.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * A DTO representing a TextExercise, for transferring data to Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseDTO(long id, String title, double maxPoints, double bonusPoints, String gradingInstructions, List<GradingCriterionDTO> gradingCriteria,
        String problemStatement, String exampleSolution) implements ExerciseBaseDTO {

    /**
     * Create a new TextExerciseDTO from a TextExercise
     */
    public static TextExerciseDTO of(@NotNull TextExercise exercise) {
        return new TextExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getGradingInstructions(),
                exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).toList(), exercise.getProblemStatement(), exercise.getExampleSolution());
    }

    /**
     * The type of the exercise. This is used by Athena to determine whether the correct exercise type was sent.
     *
     * @return "text"
     */
    public String getType() {
        return "text";
    }
}
