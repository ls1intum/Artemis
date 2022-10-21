import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { TestBed } from '@angular/core/testing';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { Component } from '@angular/core';

describe('Exercise Update Warning Service', () => {
    let updateWarningService: ExerciseUpdateWarningService;
    let loadExerciseSpy: jest.SpyInstance;
    let openSpy: jest.SpyInstance;

    const gradingInstruction = { id: 1, credits: 1, gradingScale: 'scale', instructionDescription: 'description', feedback: 'feedback', usageCount: 0 } as GradingInstruction;
    const gradingInstructionCreditsChanged = { ...gradingInstruction, credits: 3 } as GradingInstruction;
    const gradingInstructionUsageCountChanged = { ...gradingInstruction, usageCount: 2 } as GradingInstruction;
    const gradingCriterion = { id: 1, title: 'testCriteria', structuredGradingInstructions: [gradingInstruction] } as GradingCriterion;
    const gradingCriterionCreditsChanged = { ...gradingCriterion, structuredGradingInstructions: [gradingInstructionCreditsChanged] } as GradingCriterion;
    const gradingCriterionUsageCountChanged = { ...gradingCriterion, structuredGradingInstructions: [gradingInstructionUsageCountChanged] } as GradingCriterion;
    const gradingCriterionWithoutInstruction = { id: 1, title: 'testCriteria' } as GradingCriterion;
    const exercise = { id: 1 } as Exercise;
    const backupExercise = { id: 1 } as Exercise;

    beforeEach(() => {
        updateWarningService = TestBed.inject(ExerciseUpdateWarningService);

        loadExerciseSpy = jest.spyOn(updateWarningService, 'loadExercise');
        openSpy = jest.spyOn(updateWarningService, 'open');

        updateWarningService.instructionDeleted = false;
        updateWarningService.creditChanged = false;
        updateWarningService.usageCountChanged = false;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set instructionDeleted as true', () => {
        exercise.gradingCriteria = [gradingCriterionWithoutInstruction];
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.loadExercise(exercise, backupExercise);
        expect(updateWarningService.instructionDeleted).toBeTrue();
    });

    it('should set instructionDeleted as true when gradingCriteria is undefined', () => {
        exercise.gradingCriteria = undefined;
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.loadExercise(exercise, backupExercise);
        expect(updateWarningService.instructionDeleted).toBeTrue();
    });

    it('should set creditChanged as true', () => {
        exercise.gradingCriteria = [gradingCriterionCreditsChanged];
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.loadExercise(exercise, backupExercise);
        expect(updateWarningService.creditChanged).toBeTrue();
    });

    it('should set usageCountChanged as true', () => {
        exercise.gradingCriteria = [gradingCriterionUsageCountChanged];
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.loadExercise(exercise, backupExercise);
        expect(updateWarningService.usageCountChanged).toBeTrue();
    });

    it('should loadExercise and not open warning modal', () => {
        exercise.gradingCriteria = [gradingCriterion];
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.checkExerciseBeforeUpdate(exercise, backupExercise);

        expect(updateWarningService.instructionDeleted).toBeFalse();
        expect(updateWarningService.creditChanged).toBeFalse();
        expect(updateWarningService.usageCountChanged).toBeFalse();

        expect(loadExerciseSpy).toHaveBeenCalledOnce();
        expect(loadExerciseSpy).toHaveBeenCalledWith(exercise, backupExercise);
        expect(openSpy).not.toHaveBeenCalled();
    });

    it('should loadExercise and open warning model', () => {
        exercise.gradingCriteria = undefined;
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.checkExerciseBeforeUpdate(exercise, backupExercise);

        expect(loadExerciseSpy).toHaveBeenCalledWith(exercise, backupExercise);
        expect(loadExerciseSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(ExerciseUpdateWarningComponent as Component);
        expect(openSpy).toHaveBeenCalledOnce();
    });
});
