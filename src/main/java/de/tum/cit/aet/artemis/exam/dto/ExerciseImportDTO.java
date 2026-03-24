package de.tum.cit.aet.artemis.exam.dto;

import java.util.Map;
import java.util.function.Supplier;

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

    // Use a Map instead of a switch expression to avoid generating a synthetic $1 class
    private static final Map<ExerciseType, Supplier<Exercise>> EXERCISE_FACTORIES = Map.of(ExerciseType.MODELING, ModelingExercise::new, ExerciseType.TEXT, TextExercise::new,
            ExerciseType.PROGRAMMING, ProgrammingExercise::new, ExerciseType.FILE_UPLOAD, FileUploadExercise::new, ExerciseType.QUIZ, QuizExercise::new);

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
        Exercise exercise = EXERCISE_FACTORIES.get(exerciseType).get();
        exercise.setId(id);
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setMaxPoints(maxPoints);
        exercise.setBonusPoints(bonusPoints);
        return exercise;
    }
}
