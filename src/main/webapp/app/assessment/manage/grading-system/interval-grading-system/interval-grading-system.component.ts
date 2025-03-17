import { Component } from '@angular/core';
import { GradeStep } from 'app/entities/grade-step.model';
import { ModePickerComponent, ModePickerOption } from 'app/exercise/mode-picker/mode-picker.component';
import { BaseGradingSystemComponent, CsvGradeStep, GradeEditMode } from 'app/assessment/manage/grading-system/base-grading-system/base-grading-system.component';
import { parse } from 'papaparse';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';

@Component({
    selector: 'jhi-interval-grading-system',
    templateUrl: './interval-grading-system.component.html',
    styleUrls: ['./interval-grading-system.component.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        FaIconComponent,
        ArtemisTranslatePipe,
        SafeHtmlPipe,
        GradeStepBoundsPipe,
        ModePickerComponent,
        DeleteButtonDirective,
        HelpIconComponent,
    ],
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
        }

        // Remove sticky grade step, add the new step and re-add the sticky grade step.
        const stickyGradeStep = this.gradingScale.gradeSteps.pop()!;

        super.createGradeStep();
        this.gradingScale.gradeSteps.push(stickyGradeStep);

        const selectedIndex = this.gradingScale.gradeSteps.length - 2;
        this.setPercentageInterval(selectedIndex);
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
        gradeSteps.last()!.upperBoundInclusive = true;
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

        if (gradeSteps.length > 0) {
            // Prevent the total percentage from becoming less than 100.
            if (gradeSteps.last()!.upperBoundPercentage < 100) {
                gradeSteps.last()!.upperBoundPercentage = 100;
            }

            // If the first grade step is deleted, make sure the new first grade step's lower bound inclusivity is true.
            gradeSteps.first()!.lowerBoundInclusive = true;

            // If the last grade step is deleted, make sure the new last grade step's upper bound inclusivity is true.
            gradeSteps.last()!.upperBoundInclusive = true;
        }
    }

    /**
     * Parse CSV file to a list of CsvGradeStep objects
     * @param csvFile the read csv file
     * @override
     */
    parseCSVFile(csvFile: File): Promise<CsvGradeStep[]> {
        return new Promise((resolve, reject) => {
            parse(csvFile, {
                header: true,
                skipEmptyLines: true,
                dynamicTyping: false,
                complete: (results) => resolve(results.data as CsvGradeStep[]),
                error: (error) => reject(error),
            });
        });
    }

    shouldShowGradingStepsAboveMaxPointsWarning(): boolean {
        const steps = [...this.gradingScale.gradeSteps].slice(0, this.gradingScale.gradeSteps.length - 1);
        return this.isAnyGradingStepAboveMaxPoints(steps);
    }
}
