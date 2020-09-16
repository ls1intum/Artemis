import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

/**
 * The actions of the test case table:
 * - Save the test cases with the updated values.
 * - Reset all weights to 1.
 * - Trigger the submissions for all participations of the given exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-configure-grading-actions',
    template: `
        <button
            id="save-test-cases-button"
            class="btn btn-primary ml-3"
            jhiTranslate="artemisApp.programmingExercise.configureGrading.save"
            (click)="onSave.emit()"
            [disabled]="isSaving || !hasUnsavedChanges"
        ></button>
        <button
            id="reset-weights-button"
            class="btn btn-secondary ml-3"
            (click)="onReset.emit()"
            [disabled]="isSaving"
            jhiTranslate="artemisApp.programmingExercise.configureGrading.reset"
        ></button>
        <jhi-programming-exercise-re-evaluate-button [exercise]="exercise" [disabled]="isSaving"></jhi-programming-exercise-re-evaluate-button>
        <jhi-programming-exercise-trigger-all-button
            [exercise]="exercise"
            [disabled]="isSaving || !hasUpdatedTestCases"
            (onBuildTriggered)="onBuildTriggered.emit()"
        ></jhi-programming-exercise-trigger-all-button>
    `,
})
export class ProgrammingExerciseConfigureGradingActionsComponent {
    @Input() exercise: ProgrammingExercise;
    @Input() hasUnsavedChanges: boolean;
    @Input() hasUpdatedTestCases: boolean;
    @Input() isSaving: boolean;

    @Output() onSave = new EventEmitter();
    @Output() onReset = new EventEmitter();
    @Output() onBuildTriggered = new EventEmitter();
}
