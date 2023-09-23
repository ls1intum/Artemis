package de.tum.in.www1.artemis.service.dto.athena;

import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.TextExercise;

/**
 * A DTO representing a TextExercise, for transferring data to Athena
 */
public record TextExerciseDTO(long id, String title, Double maxPoints, double bonusPoints, String gradingInstructions, String problemStatement) {

    /**
     * Create a new TextExerciseDTO from a TextExercise
     */
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
