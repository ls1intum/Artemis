package de.tum.cit.aet.artemis.exercise.domain;

import com.fasterxml.jackson.annotation.JsonValue;

import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

public enum ExerciseType {

    TEXT("text"), PROGRAMMING("programming"), MODELING("modeling"), FILE_UPLOAD("file-upload"), QUIZ("quiz");

    private final String value;

    ExerciseType(String value) {
        this.value = value;
    }

    /**
     * Returns the JSON serialization value for this exercise type.
     * This matches the type discriminator values used in Exercise entity.
     *
     * @return lowercase string representation (e.g., "text", "programming", "file-upload")
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Used for human-readable string manipulations e.g. for notifications texts
     *
     * @return the exercise type as a lower case String without any special characters (e.g. FILE_UPLOAD -> "file upload")
     */
    public String getExerciseTypeAsReadableString() {
        return this.toString().toLowerCase().replace('_', ' ');
    }

    /**
     * Used to filter the exercise type using the TYPE-operator.
     *
     * @return the class corresponding to the ExerciseType
     */
    public Class<? extends Exercise> getExerciseClass() {
        return switch (this) {
            case TEXT -> TextExercise.class;
            case PROGRAMMING -> ProgrammingExercise.class;
            case MODELING -> ModelingExercise.class;
            case FILE_UPLOAD -> FileUploadExercise.class;
            case QUIZ -> QuizExercise.class;
        };
    }

    /**
     * Get the exercise type based on a class.
     *
     * @param exerciseClass the class for which the ExerciseType should be returned
     * @return the exercise type corresponding to the class
     */
    public static ExerciseType getExerciseTypeFromClass(Class<? extends Exercise> exerciseClass) {
        return switch (exerciseClass.getSimpleName()) {
            case "TextExercise" -> TEXT;
            case "ProgrammingExercise" -> PROGRAMMING;
            case "ModelingExercise" -> MODELING;
            case "FileUploadExercise" -> FILE_UPLOAD;
            case "QuizExercise" -> QUIZ;
            default -> throw new IllegalArgumentException(String.format("Received unexecpted exercise class name %s", exerciseClass.getSimpleName()));
        };
    }
}
