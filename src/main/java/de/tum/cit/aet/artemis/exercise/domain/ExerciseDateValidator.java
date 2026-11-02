package de.tum.cit.aet.artemis.exercise.domain;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * The date ordering rules shared by every exercise type, kept out of {@link Exercise} to keep that class small.
 */
public final class ExerciseDateValidator {

    private ExerciseDateValidator() {
    }

    /**
     * Validates the date ordering shared by every exercise type.
     *
     * @param exercise the exercise to validate
     * @throws BadRequestAlertException if an exam exercise has dates or the dates are not in a valid order
     */
    public static void validate(Exercise exercise) {
        // All fields are optional, so there is no error if none of them is set
        if (exercise.getReleaseDate() == null && exercise.getStartDate() == null && exercise.getDueDate() == null && exercise.getAssessmentDueDate() == null
                && exercise.getExampleSolutionPublicationDate() == null) {
            return;
        }
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("An exam exercise may not have any dates set!", exercise.getTitle(), "invalidDatesForExamExercise");
        }

        if (!exercise.hasValidTimelineDates()) {
            throw new BadRequestAlertException("The exercise dates are not valid", exercise.getTitle(), "noValidDates");
        }
    }

}
