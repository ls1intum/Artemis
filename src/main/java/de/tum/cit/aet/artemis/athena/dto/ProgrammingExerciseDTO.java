package de.tum.cit.aet.artemis.athena.dto;

import static de.tum.cit.aet.artemis.core.config.Constants.ATHENA_PROGRAMMING_EXERCISE_REPOSITORY_API_PATH;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * A DTO representing a ProgrammingExercise, for transferring data to Athena
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseDTO(long id, String title, double maxPoints, double bonusPoints, String gradingInstructions, List<GradingCriterionDTO> gradingCriteria,
        String problemStatement, String programmingLanguage, String solutionRepositoryUri, String templateRepositoryUri, String testsRepositoryUri) implements ExerciseBaseDTO {

    /**
     * Create a new TextExerciseDTO from a TextExercise
     */
    public static ProgrammingExerciseDTO of(@NotNull ProgrammingExercise exercise, String artemisServerUrl) {
        return new ProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getGradingInstructions(),
                exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).toList(), exercise.getProblemStatement(), exercise.getProgrammingLanguage().name(),
                artemisServerUrl + ATHENA_PROGRAMMING_EXERCISE_REPOSITORY_API_PATH + exercise.getId() + "/repository/solution",
                artemisServerUrl + ATHENA_PROGRAMMING_EXERCISE_REPOSITORY_API_PATH + exercise.getId() + "/repository/template",
                artemisServerUrl + ATHENA_PROGRAMMING_EXERCISE_REPOSITORY_API_PATH + exercise.getId() + "/repository/tests");
    }

    /**
     * The type of the exercise. This is used by Athena to determine whether the correct exercise type was sent.
     *
     * @return "programming"
     */
    public String getType() {
        return "programming";
    }
}
