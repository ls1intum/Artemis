import { Component } from '@angular/core';
import { GradeStep } from 'app/entities/grade-step.model';
import { ModePickerOption } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { BaseGradingSystemComponent, CsvGradeStep, GradeEditMode } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { parse } from 'papaparse';

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

    createGradeStep(): void {
        if (this.gradingScale?.gradeSteps?.length === 0) {
            // Add sticky grade step at the end.
            super.createGradeStep();

            // This is to ensure 100 percent is not a part of the sticky grade step.
            this.gradingScale.gradeSteps.first()!.lowerBoundInclusive = false;
        }

        // Remove sticky grade step, add the new step and re-add the sticky grade step.
        const stickyGradeStep = this.gradingScale.gradeSteps.pop()!;

        super.createGradeStep();
        this.gradingScale.gradeSteps.push(stickyGradeStep);

        const selectedIndex = this.gradingScale.gradeSteps.length - 2;
        this.setPercentageInterval(selectedIndex);
    }

    getDefaultGradingScale() {
        const defaultGradingScale = super.getDefaultGradingScale();
        const stickyGradeStep = {
            gradeName: '1.0+',
            lowerBoundPercentage: 100,
            upperBoundPercentage: 200,
            lowerBoundInclusive: false,
            upperBoundInclusive: true,
            isPassingGrade: true,
        };
        defaultGradingScale.gradeSteps.push(stickyGradeStep);

        this.setPoints(stickyGradeStep, true);
        this.setPoints(stickyGradeStep, false);

        return defaultGradingScale;
    }

    /**
     * Sets the inclusivity for all grade steps based on the lowerBoundInclusivity property
     * Called before a post/put request
     * @override
     */
    setInclusivity(): void {
        const gradeSteps = this.gradingScale?.gradeSteps;
        if (!(gradeSteps?.length > 0)) {
            return;
        }

        // No need to sort grade steps since they are always sorted.
        gradeSteps.forEach((gradeStep) => {
            gradeStep.lowerBoundInclusive = this.lowerBoundInclusivity;
            gradeStep.upperBoundInclusive = !this.lowerBoundInclusivity;
        });

        // Always true
        gradeSteps.first()!.lowerBoundInclusive = true;

        if (gradeSteps.length > 1) {
            // Ensure 100 percent is not a part of the sticky grade step.
            gradeSteps[gradeSteps.length - 2].upperBoundInclusive = true;
            const stickyGradeStep = gradeSteps.last()!;
            stickyGradeStep.lowerBoundInclusive = false;
            stickyGradeStep.upperBoundInclusive = true;
        }
    }

    /**
     * Sets the lower and upper percentage value of all grade steps in gradingScale.gradeSteps, starting from {@link selectedIndex}
     * until the end of the list. If {@link newPercentageInterval} is undefined or null, then the grade step at {@link selectedIndex}
     * will be unmodified, only the later grade steps will be adjusted accordingly.
     *
     * @param selectedIndex the index of the {@link GradeStep} that will be first to be modified.
     * @param newPercentageInterval the new difference between lower and upper bounds of the grade step at {@link selectedIndex},
     *                              undefined or null means that the difference will not change.
     */
    setPercentageInterval(selectedIndex: number, newPercentageInterval?: number): void {
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
                currentInterval = newPercentageInterval ?? this.getPercentageInterval(currentGradeStep);
            }

            currentGradeStep.upperBoundPercentage = currentGradeStep.lowerBoundPercentage + currentInterval;

            this.setPoints(currentGradeStep, true);
            this.setPoints(currentGradeStep, false);

            previousGradeStep = currentGradeStep;
        }
    }

    /**
     * Sets the lower and upper boundary point values of all grade steps in gradingScale.gradeSteps
     * starting from {@link selectedIndex} until the end of the list.
     *
     * @param selectedIndex the index of the {@link GradeStep} that will be first to be modified.
     * @param newPointsInterval the new difference between lower and upper bounds of the grade step at {@link selectedIndex}
     */
    setPointsInterval(selectedIndex: number, newPointsInterval: number): void {
        const gradeStep = this.gradingScale.gradeSteps[selectedIndex];
        if (gradeStep.lowerBoundPoints == undefined) {
            throw new Error(`lowerBoundPoints are not set yet for selectedIndex: '${selectedIndex}'`);
        }
        gradeStep.upperBoundPoints = gradeStep.lowerBoundPoints + newPointsInterval;
        this.setPercentage(gradeStep, false);
        this.setPercentageInterval(selectedIndex);
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
        const gradeSteps = this.gradingScale.gradeSteps;
        if (gradeSteps.length === 1) {
            // Only sticky grade step remains, prevent total percentage is less than 100.
            this.setPercentageInterval(0, 100);
        }
    }

    /**
     * Parse CSV file to a list of CsvGradeStep objects
     * @param csvFile the read csv file
     * @override
     */
    parseCSVFile(csvFile: File): Promise<CsvGradeStep[]> {
        return new Promise(async (resolve, reject) => {
            parse(csvFile, {
                header: true,
                skipEmptyLines: true,
                dynamicTyping: false,
                complete: (results) => resolve(results.data as CsvGradeStep[]),
                error: (error) => reject(error),
            });
        });
    }
}
