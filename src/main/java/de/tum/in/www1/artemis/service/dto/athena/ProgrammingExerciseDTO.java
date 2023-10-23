package de.tum.in.www1.artemis.service.dto.athena;

import static de.tum.in.www1.artemis.config.Constants.ATHENA_PROGRAMMING_EXERCISE_REPOSITORY_API_PATH;

import java.util.List;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.service.dto.GradingGriterionDTO;

/**
 * A DTO representing a ProgrammingExercise, for transferring data to Athena
 */
public record ProgrammingExerciseDTO(long id, String title, Double maxPoints, double bonusPoints, String gradingInstructions, List<GradingGriterionDTO> gradingCriteria,
        String problemStatement, String programmingLanguage, String solutionRepositoryUrl, String templateRepositoryUrl, String testsRepositoryUrl) {

    /**
     * Create a new TextExerciseDTO from a TextExercise
     */
    public static ProgrammingExerciseDTO of(@NotNull ProgrammingExercise exercise, String artemisServerUrl) {
        return new ProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getGradingInstructions(),
                exercise.getGradingCriteria().stream().map(GradingGriterionDTO::of).toList(), exercise.getProblemStatement(), exercise.getProgrammingLanguage().name(),
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
