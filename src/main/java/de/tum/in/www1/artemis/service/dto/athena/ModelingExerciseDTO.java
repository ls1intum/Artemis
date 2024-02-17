package de.tum.in.www1.artemis.service.dto.athena;

import java.util.List;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.service.dto.GradingCriterionDTO;

/**
 * A DTO representing a ModelingExercise, for transferring data to Athena
 */
public record ModelingExerciseDTO(long id, String title, double maxPoints, double bonusPoints, String gradingInstructions, List<GradingCriterionDTO> gradingCriteria,
        String problemStatement, String exampleSolution) implements ExerciseDTO {

    /**
     * Create a new ModelingExerciseDTO from a ModelingExercise
     */
    public static ModelingExerciseDTO of(@NotNull ModelingExercise exercise) {
        return new ModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getGradingInstructions(),
                exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).toList(), exercise.getProblemStatement(), exercise.getExampleSolutionModel());
    }

    /**
     * The type of the exercise. This is used by Athena to determine whether the correct exercise type was sent.
     *
     * @return "modelling"
     */
    public String getType() {
        return "modelling";
    }
}
