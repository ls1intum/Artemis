package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * DTO for importing exercises. Contains the source exercise ID and optional overrides.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseImportDTO(@NotNull Long id, @NotNull ExerciseType exerciseType, @Nullable String title, @Nullable String shortName, @Nullable Double maxPoints,
        @Nullable Double bonusPoints) {

    /**
     * Creates an ExerciseImportDTO from an existing Exercise entity.
     *
     * @param exercise the exercise to convert
     * @return the DTO representation
     */
    public static ExerciseImportDTO of(Exercise exercise) {
        return new ExerciseImportDTO(exercise.getId(), exercise.getExerciseType(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(),
                exercise.getBonusPoints());
    }

    /**
     * Creates a skeleton Exercise entity from this DTO.
     * The actual exercise import will use the ID to look up the source exercise.
     *
     * @return a new Exercise entity with basic properties set
     */
    public Exercise toEntity() {
        // Use if-else instead of switch to avoid generating a synthetic class in this record
        Exercise exercise;
        if (exerciseType == ExerciseType.MODELING) {
            exercise = new ModelingExercise();
        }
        else if (exerciseType == ExerciseType.TEXT) {
            exercise = new TextExercise();
        }
        else if (exerciseType == ExerciseType.PROGRAMMING) {
            exercise = new ProgrammingExercise();
        }
        else if (exerciseType == ExerciseType.FILE_UPLOAD) {
            exercise = new FileUploadExercise();
        }
        else if (exerciseType == ExerciseType.QUIZ) {
            exercise = new QuizExercise();
        }
        else {
            throw new IllegalArgumentException("Unknown exercise type: " + exerciseType);
        }
        exercise.setId(id);
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setMaxPoints(maxPoints);
        exercise.setBonusPoints(bonusPoints);
        return exercise;
    }
}
