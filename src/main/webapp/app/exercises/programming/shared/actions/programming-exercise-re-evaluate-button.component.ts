import { Component, Input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ButtonType } from 'app/shared/components/button.component';
import { faRedo } from '@fortawesome/free-solid-svg-icons';

/**
 * A button that re-evaluates all latest automatic results of the given programming exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-re-evaluate-button',
    template: `
        <jhi-button
            id="re-evaluate-button"
            class="ms-3"
            [disabled]="disabled || isReEvaluationRunning"
            [btnType]="ButtonType.ERROR"
            [isLoading]="isReEvaluationRunning"
            [tooltip]="'artemisApp.programmingExercise.reEvaluateTooltip'"
            [icon]="faRedo"
            [title]="'artemisApp.programmingExercise.reEvaluate'"
            [featureToggle]="FeatureToggle.ProgrammingExercises"
            (onClick)="triggerReEvaluate()"
        />
    `,
})
export class ProgrammingExerciseReEvaluateButtonComponent {
    FeatureToggle = FeatureToggle;
    ButtonType = ButtonType;
    @Input() exercise: ProgrammingExercise;
    @Input() disabled = false;

    isReEvaluationRunning = false;

    // Icons
    faRedo = faRedo;

    constructor(
        private testCaseService: ProgrammingExerciseGradingService,
        private alertService: AlertService,
    ) {}

    /**
     * Triggers the re-evaluation of the programming exercise and displays the result in the end using an alert.
     */
    triggerReEvaluate() {
        this.isReEvaluationRunning = true;
        this.testCaseService.reEvaluate(this.exercise.id!).subscribe({
            next: (updatedResultsCount: number) => {
                this.isReEvaluationRunning = false;
                this.alertService.success(`artemisApp.programmingExercise.reEvaluateSuccessful`, { number: updatedResultsCount });
            },
            error: (error: HttpErrorResponse) => {
                this.isReEvaluationRunning = false;
                this.alertService.error(`artemisApp.programmingExercise.reEvaluateFailed`, { message: error.message });
            },
        });
    }
}
