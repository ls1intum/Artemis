import { GradeStep } from 'app/entities/grade-step.model';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { GradeEditMode } from 'app/grading-system/base-grading-system/base-grading-system.component';

describe('GradeStepBoundsPipe', () => {
    const pipe = new GradeStepBoundsPipe();

    it('should format non-last step percentage', () => {
        const gradeStep: GradeStep = {
            gradeName: 'Fail',
            lowerBoundPercentage: 0,
            upperBoundPercentage: 40,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: false,
        };
        const intervalText = pipe.transform(gradeStep, GradeEditMode.PERCENTAGE, false);
        expect(intervalText).toBe('[0 - 40)');
    });

    it('should format last step percentage', () => {
        const gradeStep: GradeStep = {
            gradeName: 'Pass',
            lowerBoundPercentage: 15,
            upperBoundPercentage: 115,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const intervalText = pipe.transform(gradeStep, GradeEditMode.PERCENTAGE, true);
        expect(intervalText).toBe('[15 - &infin;)');
    });

    it('should format non-last step points', () => {
        const gradeStep: GradeStep = {
            gradeName: 'Pass',
            lowerBoundPercentage: 10,
            upperBoundPercentage: 70,
            lowerBoundPoints: 20,
            upperBoundPoints: 140,
            lowerBoundInclusive: false,
            upperBoundInclusive: true,
            isPassingGrade: false,
        };
        const intervalText = pipe.transform(gradeStep, GradeEditMode.POINTS, false);
        expect(intervalText).toBe('(20 - 140]');
    });

    it('should format last step points', () => {
        const gradeStep: GradeStep = {
            gradeName: 'Pass',
            lowerBoundPercentage: 40,
            upperBoundPercentage: 140,
            lowerBoundPoints: 80,
            upperBoundPoints: 280,
            lowerBoundInclusive: false,
            upperBoundInclusive: true,
            isPassingGrade: true,
        };
        const intervalText = pipe.transform(gradeStep, GradeEditMode.POINTS, true);
        expect(intervalText).toBe('(80 - &infin;)');
    });

    it('should return a placeholder when formatting undefined points', () => {
        const gradeStep: GradeStep = {
            gradeName: 'Pass',
            lowerBoundPercentage: 40,
            upperBoundPercentage: 140,
            lowerBoundInclusive: false,
            upperBoundInclusive: true,
            isPassingGrade: true,
        };
        const intervalText = pipe.transform(gradeStep, GradeEditMode.POINTS, true);
        expect(intervalText).toBe('-');
    });
});
