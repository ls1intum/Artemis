import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

/**
 * The actions of the grading page:
 * - Re-evaluate all results
 * - Trigger the submissions for all participations of the given exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-configure-grading-actions',
    template: `
        <jhi-programming-exercise-re-evaluate-button [exercise]="exercise" [disabled]="isSaving"></jhi-programming-exercise-re-evaluate-button>
        <jhi-programming-exercise-trigger-all-button
            [exercise]="exercise"
            [disabled]="isSaving"
            (onBuildTriggered)="onBuildTriggered.emit()"
        ></jhi-programming-exercise-trigger-all-button>
    `,
})
export class ProgrammingExerciseConfigureGradingActionsComponent {
    @Input() exercise: ProgrammingExercise;
    @Input() hasUpdatedGradingConfig: boolean;
    @Input() isSaving: boolean;

    @Output() onBuildTriggered = new EventEmitter();
}
