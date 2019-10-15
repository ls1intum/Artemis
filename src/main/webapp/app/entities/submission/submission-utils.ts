import { Submission } from 'app/entities/submission/submission.model';
import * as moment from 'moment';
import { Exercise } from 'app/entities/exercise';

/**
 * Check if a given submission is in due time of the given exercise
 *
 * @param submission
 * @param exercise
 */
export const isSubmissionInDueTime = (submission: Submission, exercise: Exercise) => {
    // If the exercise has no dueDate set, every submission is in time.
    if (!exercise.dueDate) {
        return true;
    }

    // If the submissionDate is before the dueDate of the exercise, the submission is in time.
    if (submission.submissionDate) {
        submission.submissionDate = moment(submission.submissionDate);
        return submission.submissionDate.isBefore(exercise.dueDate);
    }

    // If the submission has no submissionDate set, the submission cannot be in time.
    return false;
};
