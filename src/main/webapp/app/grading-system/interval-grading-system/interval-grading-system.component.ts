import { Component } from '@angular/core';
import { GradeStep } from 'app/entities/grade-step.model';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { BaseGradingSystemComponent } from 'app/grading-system/base-grading-system/base-grading-system.component';

enum GradeEditMode {
    POINTS,
    PERCENTAGE,
}

@Component({
    selector: 'jhi-interval-grading-system',
    templateUrl: './interval-grading-system.component.html',
    styleUrls: ['./interval-grading-system.component.scss'],
})
export class IntervalGradingSystemComponent extends BaseGradingSystemComponent {
    readonly GradeEditMode = GradeEditMode;
    gradeEditMode = GradeEditMode.PERCENTAGE;

    readonly modePickerOptions: ModePickerOption<GradeEditMode>[] = [
        {
            value: GradeEditMode.PERCENTAGE,
            labelKey: 'artemisApp.gradingSystem.intervalTab.percentageMode',
            btnClass: 'btn-secondary',
        },
        {
            value: GradeEditMode.POINTS,
            labelKey: 'artemisApp.gradingSystem.intervalTab.pointsMode',
            btnClass: 'btn-info',
        },
    ];

    /**
     * Sets the absolute points value of all grade steps in gradingScale.gradeSteps, starting from {@link selectedIndex}
     * until the end of the list.
     *
     * @param selectedIndex the index of the {@link GradeStep} that will be first to be modified.
     * @param newPercentageInterval the new difference between lower and upper bounds of the grade step at {@link selectedIndex}
     */
    setPercentageInterval(selectedIndex: number, newPercentageInterval: number): void {
        const gradeSteps = this.gradingScale.gradeSteps;
        let previousGradeStep: GradeStep | undefined = undefined;

        for (let i = selectedIndex; i < gradeSteps.length; i++) {
            const currentGradeStep = gradeSteps[i];
            let currentInterval: number;

            if (previousGradeStep) {
                // current grade step is after the selectedIndex so we need to shift lower bound
                currentInterval = this.getPercentageInterval(currentGradeStep);
                currentGradeStep.lowerBoundPercentage = previousGradeStep.upperBoundPercentage;
            } else {
                // current grade step === grade step at selectedIndex
                currentInterval = newPercentageInterval;
            }

            currentGradeStep.upperBoundPercentage = currentGradeStep.lowerBoundPercentage + currentInterval;

            this.setPoints(currentGradeStep, true);
            this.setPoints(currentGradeStep, false);

            previousGradeStep = currentGradeStep;
        }
    }

    setPointsInterval(selectedIndex: number, newPointsInterval: number): void {
        const gradeStep = this.gradingScale.gradeSteps[selectedIndex];
        if (gradeStep.lowerBoundPoints == undefined) {
            throw new Error(`lowerBoundPoints are not set yet for selectedIndex: '${selectedIndex}'`);
        }
        gradeStep.upperBoundPoints = gradeStep.lowerBoundPoints + newPointsInterval;
        this.setPercentage(gradeStep, false);
        this.setPercentageInterval(selectedIndex, this.getPercentageInterval(gradeStep));
    }

    getPercentageInterval(gradeStep: GradeStep) {
        return gradeStep.upperBoundPercentage - gradeStep.lowerBoundPercentage;
    }

    getPointsInterval(gradeStep: GradeStep) {
        if (gradeStep.upperBoundPoints == undefined || gradeStep.lowerBoundPoints == undefined) {
            return undefined;
        }
        return gradeStep.upperBoundPoints - gradeStep.lowerBoundPoints;
    }

    deleteGradeStep(index: number): void {
        this.setPercentageInterval(index, 0);
        super.deleteGradeStep(index);
    }
}
