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
    if (submission.submissionDate && exercise.dueDate) {
        // submission is in due time if submissionDate is before the dueDate of the exercise
        submission.submissionDate = moment(submission.submissionDate);
        return submission.submissionDate.isBefore(exercise.dueDate);
    } else {
        // submission is in due time if the exercise has no dueDate
        return !exercise.dueDate;
    }
};
