package de.tum.in.www1.artemis.domain.enumeration;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.math.MathExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

public enum ExerciseType {

    FILE_UPLOAD, MATH, MODELING, PROGRAMMING, QUIZ, TEXT;

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
            case FILE_UPLOAD -> FileUploadExercise.class;
            case MATH -> MathExercise.class;
            case MODELING -> ModelingExercise.class;
            case PROGRAMMING -> ProgrammingExercise.class;
            case QUIZ -> QuizExercise.class;
            case TEXT -> TextExercise.class;
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
            case "FileUploadExercise" -> FILE_UPLOAD;
            case "MathExercise" -> MATH;
            case "ModelingExercise" -> MODELING;
            case "ProgrammingExercise" -> PROGRAMMING;
            case "QuizExercise" -> QUIZ;
            case "TextExercise" -> TEXT;
            default -> throw new IllegalArgumentException(String.format("Received unexecpted exercise class name %s", exerciseClass.getSimpleName()));
        };
    }
}
