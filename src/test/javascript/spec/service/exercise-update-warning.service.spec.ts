import { ExerciseUpdateWarningService } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.service';
import { getTestBed } from '@angular/core/testing';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { Exercise } from 'app/entities/exercise.model';

describe('Exercise Update Warning Service', () => {
    let updateWarningService: ExerciseUpdateWarningService;

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
        const injector = getTestBed();
        updateWarningService = injector.get(ExerciseUpdateWarningService);

        updateWarningService.instructionDeleted = false;
        updateWarningService.creditChanged = false;
        updateWarningService.usageCountChanged = false;
    });

    it('should set instructionDeleted as true', () => {
        exercise.gradingCriteria = [gradingCriterionWithoutInstruction];
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.loadExercise(exercise, backupExercise);
        expect(updateWarningService.instructionDeleted).toBe(true);
    });

    it('should set creditChanged as true', () => {
        exercise.gradingCriteria = [gradingCriterionCreditsChanged];
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.loadExercise(exercise, backupExercise);
        expect(updateWarningService.creditChanged).toBe(true);
    });

    it('should set usageCountChanged as true', () => {
        exercise.gradingCriteria = [gradingCriterionUsageCountChanged];
        backupExercise.gradingCriteria = [gradingCriterion];
        updateWarningService.loadExercise(exercise, backupExercise);
        expect(updateWarningService.usageCountChanged).toBe(true);
    });
});
