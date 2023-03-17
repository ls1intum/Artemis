package de.tum.in.www1.artemis.domain.enumeration;

import javax.persistence.DiscriminatorValue;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

public enum ExerciseType {

    TEXT, PROGRAMMING, MODELING, FILE_UPLOAD, QUIZ;

    /**
     * Used for human-readable string manipulations e.g. for notifications texts
     *
     * @return the exercise type as a lower case String without any special characters (e.g. FILE_UPLOAD -> "file upload")
     */
    public String getExerciseTypeAsReadableString() {
        return this.toString().toLowerCase().replace('_', ' ');
    }

    /**
     * The discriminator value can be used to filter polymorphic relations, e.g. by using `exercise.class = "P"` to select only programming exercises from the exercises table.
     *
     * @return the discriminator value for the exercise type (e.g. "P" for ExerciseType.PROGRAMMING)
     */
    public String getDiscriminator() {
        final DiscriminatorValue value = getExerciseClass().getAnnotation(DiscriminatorValue.class);
        return value.value();
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
}
