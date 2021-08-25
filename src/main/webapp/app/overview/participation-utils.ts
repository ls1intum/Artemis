import { SimpleChanges } from '@angular/core';
import { getExercise, Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { findLatestResult } from 'app/shared/util/utils';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

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

export const getLatestResult = (participation: Participation) => {
    findLatestResult(participation.results);
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
 * Checks if given participation is related to a (transformation) modeling, text or file-upload exercise.
 *
 * @param participation
 */
export const isModelingOrTextOrFileUpload = (participation: Participation) => {
    if (!participation) {
        return false;
    }
    const exercise = getExercise(participation);
    return (
        exercise &&
        (exercise.type === ExerciseType.MODELING ||
            exercise.type === ExerciseType.TEXT ||
            exercise.type === ExerciseType.FILE_UPLOAD ||
            exercise.type === ExerciseType.TRANSFORMATION)
    );
};

/**
 * Checks if given participation has results.
 *
 * @param participation
 * @return {boolean}
 */
export const hasResults = (participation: Participation) => {
    return participation.results && participation.results.length > 0;
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
        submission.submissionDate = moment(submission.submissionDate);
        return submission.submissionDate.isBefore(exercise.dueDate);
    }

    // If the submission has no submissionDate set, the submission cannot be in time.
    return false;
};

/**
 * Removes the login from the repositoryURL and saves it as a helper attribute
 */
export const addUserIndependentRepositoryUrl = (participation: ProgrammingExerciseStudentParticipation) => {
    let adjustedRepositoryURL = participation.repositoryUrl || '';
    if (participation.student && participation.repositoryUrl) {
        const userName = participation.student.login + '@';
        if (participation.repositoryUrl.includes(userName)) {
            adjustedRepositoryURL = participation.repositoryUrl.replace(userName, '');
        }
    }
    participation.userIndependentRepositoryUrl = adjustedRepositoryURL;
};
