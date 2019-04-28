import { SimpleChanges } from '@angular/core';

export const hasExerciseChanged = (changes: SimpleChanges) => {
    return (
        changes.participation &&
        changes.participation.currentValue &&
        (!changes.participation.previousValue || changes.participation.previousValue.id !== changes.participation.currentValue.id)
    );
};
export const problemStatementHasChanged = (changes: SimpleChanges) => {
    return (
        changes.exercise &&
        changes.exercise.previousValue &&
        changes.exercise.currentValue &&
        changes.exercise.previousValue.problemStatement !== changes.exercise.currentValue.problemStatement
    );
};
