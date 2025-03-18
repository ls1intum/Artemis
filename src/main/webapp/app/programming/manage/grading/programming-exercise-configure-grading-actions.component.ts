import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseReEvaluateButtonComponent } from 'app/programming/shared/actions/programming-exercise-re-evaluate-button.component';
import { ProgrammingExerciseTriggerAllButtonComponent } from 'app/programming/shared/actions/programming-exercise-trigger-all-button.component';

/**
 * The actions of the grading page:
 * - Re-evaluate all results
 * - Trigger the submissions for all participations of the given exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-configure-grading-actions',
    template: `
        <jhi-programming-exercise-re-evaluate-button [exercise]="exercise" [disabled]="isSaving" />
        <jhi-programming-exercise-trigger-all-button [exercise]="exercise" [disabled]="isSaving" (onBuildTriggered)="onBuildTriggered.emit()" />
    `,
    imports: [ProgrammingExerciseReEvaluateButtonComponent, ProgrammingExerciseTriggerAllButtonComponent],
})
export class ProgrammingExerciseConfigureGradingActionsComponent {
    @Input() exercise: ProgrammingExercise;
    @Input() hasUpdatedGradingConfig: boolean;
    @Input() isSaving: boolean;

    @Output() onBuildTriggered = new EventEmitter();
}
