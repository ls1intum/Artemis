import { Component, Input, EventEmitter, Output } from '@angular/core';

/**
 * The actions of the test case table:
 * - Save the test cases with the updated values.
 * - Reset all weights to 1.
 * - Trigger the submissions for all participations of the given exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-manage-test-cases-actions',
    template: `
        <button
            id="save-test-cases-button"
            class="btn btn-primary ml-3"
            jhiTranslate="artemisApp.programmingExercise.manageTestCases.saveTestCases"
            (click)="onSaveWeights.emit()"
            [disabled]="isSaving || !hasUnsavedChanges"
        ></button>
        <button
            id="reset-weights-button"
            class="btn btn-secondary ml-3"
            (click)="onResetWeights.emit()"
            [disabled]="disableResetWeights || isSaving"
            jhiTranslate="artemisApp.programmingExercise.manageTestCases.resetWeights"
        ></button>
        <jhi-programming-exercise-trigger-all-button
            [exerciseId]="exerciseId"
            [disabled]="isSaving || !hasUpdatedTestCases"
            (onBuildTriggered)="onBuildTriggered.emit()"
        ></jhi-programming-exercise-trigger-all-button>
    `,
})
export class ProgrammingExerciseManageTestCasesActionsComponent {
    @Input() exerciseId: number;
    @Input() hasUnsavedChanges: boolean;
    @Input() hasUpdatedTestCases: boolean;
    @Input() isSaving: boolean;
    @Input() disableResetWeights: boolean;

    @Output() onSaveWeights = new EventEmitter();
    @Output() onResetWeights = new EventEmitter();
    @Output() onBuildTriggered = new EventEmitter();
}
