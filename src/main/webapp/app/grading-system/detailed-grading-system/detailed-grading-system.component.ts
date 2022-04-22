import { Component } from '@angular/core';
import { BaseGradingSystemComponent } from 'app/grading-system/base-grading-system/base-grading-system.component';

@Component({
    selector: 'jhi-detailed-grading-system',
    templateUrl: './detailed-grading-system.component.html',
    styleUrls: ['./detailed-grading-system.component.scss'],
})
export class DetailedGradingSystemComponent extends BaseGradingSystemComponent {
    setInclusivity(): void {
        const gradeSteps = this.gradingScale.gradeSteps;
        // copy the grade steps in a separate array, so they don't get dynamically updated when sorting
        let sortedGradeSteps = gradeSteps.slice();
        sortedGradeSteps = this.gradingSystemService.sortGradeSteps(sortedGradeSteps);

        gradeSteps.forEach((gradeStep) => {
            if (this.lowerBoundInclusivity) {
                gradeStep.lowerBoundInclusive = true;
                gradeStep.upperBoundInclusive = sortedGradeSteps.last()!.gradeName === gradeStep.gradeName;
            } else {
                gradeStep.lowerBoundInclusive = sortedGradeSteps.first()!.gradeName === gradeStep.gradeName;
                gradeStep.upperBoundInclusive = true;
            }
        });
    }
}
