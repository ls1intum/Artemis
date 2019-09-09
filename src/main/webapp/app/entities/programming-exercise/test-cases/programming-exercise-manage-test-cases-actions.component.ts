import { Component, Input, EventEmitter, Output } from '@angular/core';

@Component({
    selector: 'jhi-programming-exercise-manage-test-cases-actions',
    template: `
        <button
            id="save-weights-button"
            class="btn btn-primary ml-3"
            jhiTranslate="artemisApp.programmingExercise.manageTestCases.saveTestCases"
            (click)="onSaveWeights.emit()"
            [disabled]="isSaving || !hasUnsavedChanges"
        ></button>
        <button
            class="btn btn-secondary ml-3"
            (click)="onResetWeights.emit()"
            [disabled]="isSaving"
            jhiTranslate="artemisApp.programmingExercise.manageTestCases.resetWeights"
        ></button>
        <jhi-programming-exercise-trigger-all-button
            id="trigger-submission-run-button"
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

    @Output() onSaveWeights = new EventEmitter();
    @Output() onResetWeights = new EventEmitter();
    @Output() onBuildTriggered = new EventEmitter();
}
