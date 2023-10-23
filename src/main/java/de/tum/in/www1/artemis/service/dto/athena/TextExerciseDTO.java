package de.tum.in.www1.artemis.service.dto.athena;

import java.util.List;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.service.dto.GradingGriterionDTO;

/**
 * A DTO representing a TextExercise, for transferring data to Athena
 */
public record TextExerciseDTO(long id, String title, Double maxPoints, double bonusPoints, String gradingInstructions, List<GradingGriterionDTO> gradingCriteria,
        String problemStatement, String exampleSolution) {

    /**
     * Create a new TextExerciseDTO from a TextExercise
     */
    public static TextExerciseDTO of(@NotNull TextExercise exercise) {
        return new TextExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getGradingInstructions(),
                exercise.getGradingCriteria().stream().map(GradingGriterionDTO::of).toList(), exercise.getProblemStatement(), exercise.getExampleSolution());
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
