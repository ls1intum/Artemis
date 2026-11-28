package de.tum.cit.aet.artemis.exercise.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

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
        return this.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /**
     * Get the exercise type based on a class.
     *
     * @param exerciseClass the class for which the ExerciseType should be returned
     * @return the exercise type corresponding to the class
     */
    public static ExerciseType getExerciseTypeFromClass(Class<?> exerciseClass) {
        return switch (exerciseClass.getSimpleName()) {
            case "TextExercise" -> TEXT;
            case "ProgrammingExercise" -> PROGRAMMING;
            case "ModelingExercise" -> MODELING;
            case "FileUploadExercise" -> FILE_UPLOAD;
            case "QuizExercise" -> QUIZ;
            default -> throw new IllegalArgumentException("Received unexecpted exercise class name %s".formatted(exerciseClass.getSimpleName()));
        };
    }
}
