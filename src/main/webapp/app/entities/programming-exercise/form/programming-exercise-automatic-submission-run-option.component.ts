import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from '../programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-automatic-submission-run-option',
    template: `
        <div class="form-check">
            <label class="form-check-label" for="field_automaticSubmissionRunAfterDueDate">
                <input
                    class="form-check-input"
                    type="checkbox"
                    name="automaticSubmissionRunAfterDueDate"
                    id="field_automaticSubmissionRunAfterDueDate"
                    [disabled]="!programmingExercise.dueDate"
                    [ngModel]="!!programmingExercise.automaticSubmissionRunDate"
                    (ngModelChange)="toggleAutomaticSubmissionRun()"
                    checked
                />
                <span jhiTranslate="artemisApp.programmingExercise.automaticSubmissionRunAfterDueDate">Automatic Submission Run After Due Date Passed</span>
                <fa-icon
                    icon="question-circle"
                    class="text-secondary"
                    placement="top"
                    ngbTooltip="{{ 'artemisApp.programmingExercise.automaticSubmissionRunAfterDueDate.description' | translate }}"
                ></fa-icon>
            </label>
        </div>
    `,
})
export class ProgrammingExerciseAutomaticSubmissionRunOptionComponent {
    @Input() programmingExercise: ProgrammingExercise;
    @Output() onProgrammingExerciseUpdate = new EventEmitter<ProgrammingExercise>();

    /**
     * We currently don't allow the free setting of the automatic submission run date setting, but set it to one hour after the due date.
     * The method will return immediately if there is no dueDate set.
     *
     * Will emit the updated programming exercise.
     */
    public toggleAutomaticSubmissionRun() {
        if (!this.programmingExercise.dueDate) {
            return;
        }
        const updatedProgrammingExercise = {
            ...this.programmingExercise,
            automaticSubmissionRunDate: this.programmingExercise.automaticSubmissionRunDate ? null : this.programmingExercise.dueDate.add(1, 'hours'),
        };
        this.onProgrammingExerciseUpdate.emit(updatedProgrammingExercise);
    }
}
