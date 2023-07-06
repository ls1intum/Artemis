package de.tum.in.www1.artemis.service.dto.athena;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.TextExercise;

/**
 * Data Transfer Object used for communication with Athena.
 */
public record TextExerciseDTO(long id, String title, double maxPoints, double bonusPoints, String gradingInstructions, String problemStatement) {

    public static TextExerciseDTO of(@NotNull TextExercise exercise) {
        return new TextExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getGradingInstructions(),
                exercise.getProblemStatement());
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
