import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { TestBed } from '@angular/core/testing';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import { Component } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';

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
    let exercise: Exercise;
    let backupExercise: Exercise;

    beforeEach(() => {
        updateWarningService = TestBed.inject(ExerciseUpdateWarningService);

        loadExerciseSpy = jest.spyOn(updateWarningService, 'loadExercise');
        openSpy = jest.spyOn(updateWarningService, 'open');

        updateWarningService.instructionDeleted = false;
        updateWarningService.creditChanged = false;
        updateWarningService.usageCountChanged = false;
        updateWarningService.immediateReleaseWarning = '';

        exercise = { id: 1 } as Exercise;
        backupExercise = { id: 1 } as Exercise;
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

    it.each([
        { newExercise: {} as ProgrammingExercise, oldExercise: {} as ProgrammingExercise, expectedMessage: 'artemisApp.exercise.noReleaseAndStartDateWarning' },
        { newExercise: { startDate: dayjs() } as ProgrammingExercise, oldExercise: {} as ProgrammingExercise, expectedMessage: 'artemisApp.exercise.noReleaseDateWarning' },
        { newExercise: { releaseDate: dayjs() } as ProgrammingExercise, oldExercise: {} as ProgrammingExercise, expectedMessage: '' },
        { newExercise: { id: 1 } as ProgrammingExercise, oldExercise: { id: 1 } as ProgrammingExercise, expectedMessage: '' },
        { newExercise: { id: 1, releaseDate: dayjs() } as ProgrammingExercise, oldExercise: { id: 1 } as ProgrammingExercise, expectedMessage: '' },
        {
            newExercise: { id: 1 } as ProgrammingExercise,
            oldExercise: { id: 1, releaseDate: dayjs() } as ProgrammingExercise,
            expectedMessage: 'artemisApp.exercise.noReleaseAndStartDateWarning',
        },
        {
            newExercise: { id: 1, startDate: dayjs() } as ProgrammingExercise,
            oldExercise: { id: 1, releaseDate: dayjs() } as ProgrammingExercise,
            expectedMessage: 'artemisApp.exercise.noReleaseDateWarning',
        },
        { newExercise: { id: 1, releaseDate: dayjs() } as ProgrammingExercise, oldExercise: { id: 1, releaseDate: dayjs() } as ProgrammingExercise, expectedMessage: '' },
    ])('should correctly ask user about exercise without release date', ({ newExercise, oldExercise, expectedMessage }) => {
        updateWarningService.checkExerciseBeforeUpdate(newExercise, oldExercise, false);

        expect(updateWarningService.immediateReleaseWarning).toBe(expectedMessage);
    });

    it('should not check in test course', () => {
        exercise.gradingCriteria = [gradingCriterionUsageCountChanged];
        exercise.releaseDate = undefined;
        exercise.course = { testCourse: true };
        backupExercise.gradingCriteria = [gradingCriterion];
        backupExercise.releaseDate = dayjs();
        updateWarningService.checkExerciseBeforeUpdate(exercise, backupExercise, false);

        expect(updateWarningService.usageCountChanged).toBeFalse();
        expect(updateWarningService.immediateReleaseWarning).toBe('');
    });

    it('should not check releaseDate but grading criteria in exam', () => {
        exercise.gradingCriteria = [gradingCriterionUsageCountChanged];
        exercise.releaseDate = undefined;
        backupExercise.gradingCriteria = [gradingCriterion];
        backupExercise.releaseDate = dayjs();
        updateWarningService.checkExerciseBeforeUpdate(exercise, backupExercise, true);

        expect(updateWarningService.usageCountChanged).toBeTrue();
        expect(updateWarningService.immediateReleaseWarning).toBe('');
    });

    it('should not check releaseDate in Exam', () => {
        backupExercise.releaseDate = dayjs();
        updateWarningService.isExamMode = true;
        updateWarningService.checkImmediateRelease(exercise, backupExercise);

        expect(updateWarningService.immediateReleaseWarning).toBe('');
    });

    it('should loadExercise and not open warning modal', () => {
        exercise.gradingCriteria = [gradingCriterion];
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.checkExerciseBeforeUpdate(exercise, backupExercise, false);

        expect(updateWarningService.instructionDeleted).toBeFalse();
        expect(updateWarningService.creditChanged).toBeFalse();
        expect(updateWarningService.usageCountChanged).toBeFalse();
        expect(updateWarningService.immediateReleaseWarning).toBe('');

        expect(loadExerciseSpy).toHaveBeenCalledOnce();
        expect(loadExerciseSpy).toHaveBeenCalledWith(exercise, backupExercise);
        expect(openSpy).not.toHaveBeenCalled();
    });

    it('should loadExercise and open warning model', () => {
        exercise.gradingCriteria = undefined;
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.checkExerciseBeforeUpdate(exercise, backupExercise, false);

        expect(loadExerciseSpy).toHaveBeenCalledOnce();
        expect(loadExerciseSpy).toHaveBeenCalledWith(exercise, backupExercise);
        expect(openSpy).toHaveBeenCalledWith(ExerciseUpdateWarningComponent as Component);
        expect(openSpy).toHaveBeenCalledOnce();
    });
});
