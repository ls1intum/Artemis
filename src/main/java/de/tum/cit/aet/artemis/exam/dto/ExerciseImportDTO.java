package de.tum.cit.aet.artemis.exam.dto;

import java.util.function.Consumer;

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
        Exercise exercise = switch (exerciseType) {
            case MODELING -> new ModelingExercise();
            case TEXT -> new TextExercise();
            case PROGRAMMING -> new ProgrammingExercise();
            case FILE_UPLOAD -> new FileUploadExercise();
            case QUIZ -> new QuizExercise();
        };

        exercise.setId(id);
        setIfNotNull(title, exercise::setTitle);
        setIfNotNull(shortName, exercise::setShortName);
        setIfNotNull(maxPoints, exercise::setMaxPoints);
        setIfNotNull(bonusPoints, exercise::setBonusPoints);

        return exercise;
    }

    private static <T> void setIfNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
