import { Participation, getExercise } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { SimpleChanges } from '@angular/core';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { orderBy as _orderBy } from 'lodash-es';
import { isAIResultAndIsBeingProcessed } from 'app/exercises/shared/result/result.utils';

/**
 * Check if the participation has changed.
 * This includes the first change (undefined -> participation)!
 * @param changes
 */
export const hasParticipationChanged = (changes: SimpleChanges) => {
    return (
        changes.participation &&
        changes.participation.currentValue &&
        (!changes.participation.previousValue || changes.participation.previousValue.id !== changes.participation.currentValue.id)
    );
};
export const hasTemplateParticipationChanged = (changes: SimpleChanges) => {
    return (
        changes.templateParticipation &&
        changes.templateParticipation.currentValue &&
        (!changes.templateParticipation.previousValue || changes.templateParticipation.previousValue.id !== changes.templateParticipation.currentValue.id)
    );
};
export const hasSolutionParticipationChanged = (changes: SimpleChanges) => {
    return (
        changes.solutionParticipation &&
        changes.solutionParticipation.currentValue &&
        (!changes.solutionParticipation.previousValue || changes.solutionParticipation.previousValue.id !== changes.solutionParticipation.currentValue.id)
    );
};
/**
 * Checks if given participation is related to a programming or quiz exercise.
 *
 * @param participation
 */
export const isProgrammingOrQuiz = (participation: Participation) => {
    if (!participation) {
        return false;
    }
    const exercise = getExercise(participation);
    return exercise && (exercise.type === ExerciseType.PROGRAMMING || exercise.type === ExerciseType.QUIZ);
};
/**
 * Checks if given participation is related to a modeling, text or file-upload exercise.
 *
 * @param participation
 */
export const isModelingOrTextOrFileUpload = (participation: Participation) => {
    if (!participation) {
        return false;
    }
    const exercise = getExercise(participation);
    return exercise && (exercise.type === ExerciseType.MODELING || exercise.type === ExerciseType.TEXT || exercise.type === ExerciseType.FILE_UPLOAD);
};
/**
 * Checks if given participation has results.
 *
 * @param participation
 * @return {boolean}
 */
export const hasResults = (participation: Participation) => {
    return participation.results?.length;
};
/**
 * Check if a given participation is in due time of the given exercise based on its submission at index position 0.
 * Before the method is called, it must be ensured that the submission at index position 0 is suitable to check if
 * the participation is in due time of the exercise.
 *
 * @param participation
 * @param exercise
 */
export const isParticipationInDueTime = (participation: Participation, exercise: Exercise): boolean => {
    // If the exercise has no dueDate set, every submission is in time.
    if (!exercise.dueDate) {
        return true;
    }

    // If the participation has no submission, it cannot be in due time.
    if (participation.submissions == undefined || participation.submissions.length <= 0) {
        return false;
    }

    // If the submissionDate is before the dueDate of the exercise, the submission is in time.
    const submission = participation.submissions[0];
    if (submission.submissionDate) {
        submission.submissionDate = dayjs(submission.submissionDate);
        return submission.submissionDate.isBefore(getExerciseDueDate(exercise, participation));
    }

    // If the submission has no submissionDate set, the submission cannot be in time.
    return false;
};

/**
 * Returns the latest result of a given student participation.
 *
 * @param participation
 * @param showUngradedResults
 */
export function getLatestResultOfStudentParticipation(
    participation: StudentParticipation | undefined,
    showUngradedResults: boolean,
    showAthenaPreliminaryFeedback: boolean = false,
): Result | undefined {
    if (!participation) {
        return undefined;
    }

    // Sort participation results by completionDate desc.
    if (participation.results) {
        participation.results = _orderBy(participation.results, 'completionDate', 'desc');
    }

    // The latest result is the first rated result in the sorted array (=newest) or any result if the option is active to show ungraded results.
    const latestResult = participation.results?.find(
        (result) => showUngradedResults || result.rated === true || (showAthenaPreliminaryFeedback && isAIResultAndIsBeingProcessed(result)),
    );
    // Make sure that the participation result is connected to the newest result.
    return latestResult ? { ...latestResult, participation: participation } : undefined;
}
