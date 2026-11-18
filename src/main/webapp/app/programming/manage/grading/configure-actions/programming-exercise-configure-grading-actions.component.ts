import { Component, input, output } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseReEvaluateButtonComponent } from 'app/programming/shared/actions/re-evaluate-button/programming-exercise-re-evaluate-button.component';
import { ProgrammingExerciseTriggerAllButtonComponent } from 'app/programming/shared/actions/trigger-all-button/programming-exercise-trigger-all-button.component';

/**
 * The actions of the grading page:
 * - Re-evaluate all results
 * - Trigger the submissions for all participations of the given exercise.
 */
@Component({
    selector: 'jhi-programming-exercise-configure-grading-actions',
    template: `
        <jhi-programming-exercise-re-evaluate-button [exercise]="exercise()" [disabled]="isSaving()" />
        <jhi-programming-exercise-trigger-all-button [exercise]="exercise()" [disabled]="isSaving()" (onBuildTriggered)="onBuildTriggered.emit()" />
    `,
    imports: [ProgrammingExerciseReEvaluateButtonComponent, ProgrammingExerciseTriggerAllButtonComponent],
})
export class ProgrammingExerciseConfigureGradingActionsComponent {
    readonly exercise = input.required<ProgrammingExercise>();
    readonly hasUpdatedGradingConfig = input.required<boolean>();
    readonly isSaving = input.required<boolean>();

    readonly onBuildTriggered = output();
}
