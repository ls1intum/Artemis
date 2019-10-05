import { SimpleChanges } from '@angular/core';
import { Exercise } from 'app/entities/exercise/exercise.model';

export const hasExerciseChanged = (changes: SimpleChanges) => {
    return changes.exercise && changes.exercise.currentValue && (!changes.exercise.previousValue || changes.exercise.previousValue.id !== changes.exercise.currentValue.id);
};

export const problemStatementHasChanged = (changes: SimpleChanges) => {
    return (
        changes.exercise &&
        changes.exercise.currentValue &&
        (!changes.exercise.previousValue || changes.exercise.previousValue.problemStatement !== changes.exercise.currentValue.problemStatement)
    );
};

/**
 * Checks if the due date of a given exercise lies in the past. If there is no due date it evaluates to false.
 *
 * @param exercise
 * @return {boolean}
 */
export const hasExerciseDueDatePassed = (exercise: Exercise): boolean => {
    if (exercise.dueDate == null) return false;
    return exercise.dueDate.isBefore();
};

/**
 * Checks if the given exercise has student participations.
 *
 * @param exercise
 * @return {boolean}
 */
export const hasStudentParticipations = (exercise: Exercise) => {
    return exercise.studentParticipations && exercise.studentParticipations.length > 0;
};
