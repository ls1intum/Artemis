package de.tum.in.www1.artemis.service.dto.athena;

import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * A DTO representing a ProgrammingExercise, for transferring data to Athena
 */
public record ProgrammingExerciseDTO(long id, String title, Double maxPoints, double bonusPoints, String gradingInstructions, String problemStatement, String programmingLanguage,
        String solutionRepositoryUrl, String templateRepositoryUrl, String testsRepositoryUrl) {

    @Value("${server.url}")
    private static String artemisServerUrl;

    /**
     * Create a new TextExerciseDTO from a TextExercise
     */
    public static ProgrammingExerciseDTO of(@NotNull ProgrammingExercise exercise) {
        return new ProgrammingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getGradingInstructions(),
                exercise.getProblemStatement(), exercise.getProgrammingLanguage().name(),
                artemisServerUrl + "/api/public/athena/programming-exercises/" + exercise.getId() + "/repository/solution",
                artemisServerUrl + "/api/public/athena/programming-exercises/" + exercise.getId() + "/repository/template",
                artemisServerUrl + "/api/public/athena/programming-exercises/" + exercise.getId() + "/repository/tests");
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
