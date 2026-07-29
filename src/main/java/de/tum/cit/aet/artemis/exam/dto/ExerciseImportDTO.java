package de.tum.cit.aet.artemis.exam.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
     * Deserialization entry point accepting the current {@code exerciseType} key as well as the legacy {@code type} key
     * still sent by stale cached client tabs (the pre-DTO wire's {@code @JsonTypeInfo} discriminator; the values are
     * identical). A plain {@code @JsonAlias} would bind a body carrying BOTH keys by JSON member order, silently
     * importing the wrong exercise type on disagreement — so a conflict is rejected instead (Jackson surfaces the
     * {@link IllegalArgumentException} as a 400).
     *
     * @param id           the id of the source exercise
     * @param exerciseType the exercise type (current key)
     * @param legacyType   the exercise type as sent under the legacy {@code type} key
     * @param title        the optional title override
     * @param shortName    the optional short name override
     * @param maxPoints    the optional max points override
     * @param bonusPoints  the optional bonus points override
     * @return the validated DTO
     */
    @JsonCreator
    public static ExerciseImportDTO fromJson(@JsonProperty("id") Long id, @JsonProperty("exerciseType") ExerciseType exerciseType, @JsonProperty("type") ExerciseType legacyType,
            @JsonProperty("title") String title, @JsonProperty("shortName") String shortName, @JsonProperty("maxPoints") Double maxPoints,
            @JsonProperty("bonusPoints") Double bonusPoints) {
        if (exerciseType != null && legacyType != null && exerciseType != legacyType) {
            throw new IllegalArgumentException("Conflicting exercise type discriminators: exerciseType=" + exerciseType + " vs legacy type=" + legacyType);
        }
        return new ExerciseImportDTO(id, exerciseType != null ? exerciseType : legacyType, title, shortName, maxPoints, bonusPoints);
    }

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
        Exercise exercise = createExerciseByType(exerciseType);

        exercise.setId(id);
        if (title != null) {
            exercise.setTitle(title);
        }
        if (shortName != null) {
            exercise.setShortName(shortName);
        }
        if (maxPoints != null) {
            exercise.setMaxPoints(maxPoints);
        }
        if (bonusPoints != null) {
            exercise.setBonusPoints(bonusPoints);
        }

        return exercise;
    }

    private static Exercise createExerciseByType(ExerciseType type) {
        if (type == ExerciseType.MODELING) {
            return new ModelingExercise();
        }
        else if (type == ExerciseType.TEXT) {
            return new TextExercise();
        }
        else if (type == ExerciseType.PROGRAMMING) {
            return new ProgrammingExercise();
        }
        else if (type == ExerciseType.FILE_UPLOAD) {
            return new FileUploadExercise();
        }
        else if (type == ExerciseType.QUIZ) {
            return new QuizExercise();
        }
        throw new IllegalArgumentException("Unknown exercise type: " + type);
    }
}
