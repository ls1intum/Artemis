package de.tum.cit.aet.artemis.web.rest.dto.metrics;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.enumeration.DifficultyLevel;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseMode;
import de.tum.cit.aet.artemis.domain.enumeration.IncludedInOverallScore;

/**
 * DTO for exercise information.
 *
 * @param id                     the id of the exercise
 * @param shortName              shortTitle the short title of the exercise
 * @param title                  title the title of the exercise
 * @param start                  the start date of the exercise
 * @param due                    the due date of the exercise
 * @param maxPoints              the maximum achievable points of the exercise
 * @param includedInOverallScore whether the exercise is included in the overall score
 * @param difficulty             the difficulty level of the exercise
 * @param exerciseMode           the mode of the exercise
 * @param type                   the type of the exercise
 * @param allowOnlineEditor      whether the online editor is allowed for the programming exercise, null for non-programming exercises
 * @param allowOfflineIde        whether the offline IDE is allowed for the programming exercise, null for non-programming exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseInformationDTO(long id, String shortName, String title, ZonedDateTime start, ZonedDateTime due, Double maxPoints,
        IncludedInOverallScore includedInOverallScore, DifficultyLevel difficulty, ExerciseMode exerciseMode, Class<? extends Exercise> type, Boolean allowOnlineEditor,
        Boolean allowOfflineIde) {

    /**
     * Create a new ExerciseInformationDTO from an exercise.
     *
     * @param exercise the exercise to create the DTO from
     * @return the new ExerciseInformationDTO
     */
    public static <E extends Exercise> ExerciseInformationDTO of(E exercise) {
        var startDate = exercise.getStartDate();
        if (startDate == null) {
            startDate = exercise.getReleaseDate();
        }

        Boolean allowOnlineEditor = null;
        Boolean allowOfflineIde = null;
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            allowOnlineEditor = programmingExercise.isAllowOnlineEditor();
            allowOfflineIde = programmingExercise.isAllowOfflineIde();
        }

        return new ExerciseInformationDTO(exercise.getId(), exercise.getShortName(), exercise.getTitle(), startDate, exercise.getDueDate(), exercise.getMaxPoints(),
                exercise.getIncludedInOverallScore(), exercise.getDifficulty(), exercise.getMode(), exercise.getClass(), allowOnlineEditor, allowOfflineIde);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (other instanceof ExerciseInformationDTO otherDTO) {
            // Compare all fields for equality, for dates the isEqual method is used to compare the date and time
            return id == otherDTO.id && shortName.equals(otherDTO.shortName) && title.equals(otherDTO.title) && start.isEqual(otherDTO.start) && due.isEqual(otherDTO.due)
                    && includedInOverallScore.equals(otherDTO.includedInOverallScore) && maxPoints.equals(otherDTO.maxPoints) && difficulty.equals(otherDTO.difficulty)
                    && exerciseMode.equals(otherDTO.exerciseMode) && type.equals(otherDTO.type);
        }

        return false;
    }
}
