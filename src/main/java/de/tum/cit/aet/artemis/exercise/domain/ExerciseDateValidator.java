package de.tum.cit.aet.artemis.exercise.domain;

import static de.tum.cit.aet.artemis.core.util.DateUtil.validateStrictDateSequence;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

        var releaseDate = exercise.getReleaseDate();
        var startDate = exercise.getStartDate();
        var dueDate = exercise.getDueDate();
        var assessmentDueDate = exercise.getAssessmentDueDate();
        var exampleSolutionPublicationDate = exercise.getExampleSolutionPublicationDate();

        boolean releaseDateValid = validateStrictDateSequence(List.of(), releaseDate, Arrays.asList(startDate, dueDate, assessmentDueDate, exampleSolutionPublicationDate));
        boolean startDateValid = validateStrictDateSequence(Collections.singletonList(releaseDate), startDate,
                Arrays.asList(dueDate, assessmentDueDate, exampleSolutionPublicationDate));
        boolean dueDateValid = validateStrictDateSequence(Arrays.asList(releaseDate, startDate), dueDate, Arrays.asList(assessmentDueDate, exampleSolutionPublicationDate));
        boolean assessmentDueDateValid = validateAssessmentDueDate(exercise);
        boolean exampleSolutionPublicationDateValid = validateStrictDateSequence(Arrays.asList(releaseDate, startDate, dueDate, assessmentDueDate), exampleSolutionPublicationDate,
                List.of());

        if (!(releaseDateValid && startDateValid && dueDateValid && assessmentDueDateValid && exampleSolutionPublicationDateValid)) {
            throw new BadRequestAlertException("The exercise dates are not valid", exercise.getTitle(), "noValidDates");
        }
    }

    private static boolean validateAssessmentDueDate(Exercise exercise) {
        if (exercise.getAssessmentDueDate() == null) {
            return true;
        }
        if (exercise.getDueDate() == null) {
            return false;
        }
        return validateStrictDateSequence(Arrays.asList(exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate()), exercise.getAssessmentDueDate(),
                Collections.singletonList(exercise.getExampleSolutionPublicationDate()));
    }
}
