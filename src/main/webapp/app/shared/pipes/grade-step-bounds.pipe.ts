import { Pipe, PipeTransform } from '@angular/core';
import { GradeStep } from 'app/entities/grade-step.model';
import { GradeEditMode } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { round } from 'app/shared/util/utils';

@Pipe({
    name: 'gradeStepBounds',
    pure: false,
})
export class GradeStepBoundsPipe implements PipeTransform {
    /**
     * Rounds a number to two decimal places
     *
     * @param num the number to be rounded
     */
    static round(num?: number) {
        if (num == undefined) {
            return;
        }
        return round(num, 2);
    }

    /**
     * Returns the interval representation of the given grade step indicating which ends are open and which ones are closed
     * by '(', ')' and '[', ']' respectively.
     *
     * @param gradeStep for which the interval should be defined
     * @param gradeEditMode selects whether points or percentages are shown
     * @param isLast true for last (sticky) grade step in a grading scale to cover infinity
     * @returns interval representation of the grade step
     */
    transform(gradeStep: GradeStep, gradeEditMode: GradeEditMode, isLast = false): string {
        let lowerBound: number;
        let upperBound: number;
        switch (gradeEditMode) {
            case GradeEditMode.PERCENTAGE:
                lowerBound = gradeStep.lowerBoundPercentage;
                upperBound = gradeStep.upperBoundPercentage;
                break;
            case GradeEditMode.POINTS:
                if (gradeStep.lowerBoundPoints == undefined || gradeStep.upperBoundPoints == undefined) {
                    return '-';
                }
                lowerBound = gradeStep.lowerBoundPoints;
                upperBound = gradeStep.upperBoundPoints;
                break;
        }
        const roundFunc = GradeStepBoundsPipe.round;
        const lowerBoundText = `${gradeStep.lowerBoundInclusive ? '[' : '('}${roundFunc(lowerBound)}`;

        let upperBoundText: string;
        if (isLast) {
            // This is the last (sticky) step so handle infinity case.
            upperBoundText = '&infin;)';
        } else {
            upperBoundText = `${roundFunc(upperBound)}${gradeStep.upperBoundInclusive ? ']' : ')'}`;
        }

        return `${lowerBoundText} - ${upperBoundText}`;
    }
}
