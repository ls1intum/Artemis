import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

/**
 * The actions of the test case table:
 * - Save the test cases with the updated values.
 * - Reset all weights to 1.
 */
@Component({
    selector: 'jhi-programming-exercise-grading-table-actions',
    template: `
        <button
            id="save-table-button"
            class="btn btn-primary ml-3 my-1"
            jhiTranslate="artemisApp.programmingExercise.configureGrading.save"
            (click)="onSave.emit()"
            [disabled]="isSaving || !hasUnsavedChanges"
        ></button>
        <button
            id="reset-table-button"
            class="btn btn-secondary ml-3 my-1"
            (click)="onReset.emit()"
            [disabled]="isSaving"
            jhiTranslate="artemisApp.programmingExercise.configureGrading.reset"
        ></button>
    `,
})
export class ProgrammingExerciseGradingTableActionsComponent {
    @Input() exercise: ProgrammingExercise;
    @Input() hasUnsavedChanges: boolean;
    @Input() isSaving: boolean;

    @Output() onSave = new EventEmitter();
    @Output() onReset = new EventEmitter();
}
