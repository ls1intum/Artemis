package de.tum.cit.aet.artemis.athena.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.service.dto.GradingCriterionDTO;

/**
 * A DTO representing a ModelingExercise, for transferring data to Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingExerciseDTO(long id, String title, double maxPoints, double bonusPoints, String gradingInstructions, List<GradingCriterionDTO> gradingCriteria,
        String problemStatement, String exampleSolution) implements ExerciseBaseDTO {

    /**
     * Create a new ModelingExerciseDTO from a ModelingExercise
     *
     * @param exercise The exercise for which a ModelingExerciseDTO should be constructed
     * @return The ModelingExerciseDTO representation of the provided exercise
     */
    public static ModelingExerciseDTO of(@NotNull ModelingExercise exercise) {
        return new ModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getGradingInstructions(),
                exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).toList(), exercise.getProblemStatement(), exercise.getExampleSolutionModel());
    }

    /**
     * Retrieve the type of the exercise. This is used by Athena to determine whether the correct exercise type was sent.
     *
     * @return "modeling"
     */
    public String getType() {
        return "modeling";
    }
}
