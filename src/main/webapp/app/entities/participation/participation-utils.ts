import { SimpleChanges } from '@angular/core';
import { InitializationState, Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result/result.model';
import { ExerciseType } from 'app/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

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

export const getLatestResult = (participation: Participation): Result | null => {
    return participation.results ? participation.results.reduce((currentMax, result) => (result.id > currentMax.id ? result : currentMax)) : null;
};

/**
 * Checks if given participation is related to a modeling, text or file_upload exercise.
 *
 * @param participation
 */
export const isModelingOrTextOrFileUpload = (participation: StudentParticipation) => {
    return (
        participation.initializationState === InitializationState.FINISHED &&
        participation.exercise &&
        (participation.exercise.type === ExerciseType.MODELING || participation.exercise.type === ExerciseType.TEXT || participation.exercise.type === ExerciseType.FILE_UPLOAD)
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
