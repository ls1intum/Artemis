import { SimpleChanges } from '@angular/core';
import { Participation } from 'app/entities/participation/participation.model';
import { differenceBy as _differenceBy } from 'lodash';
import { Result } from 'app/entities/result';

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
