import * as moment from 'moment';
import { Component, EventEmitter, Input, Output, HostBinding, ViewChild } from '@angular/core';
import { ProgrammingExercise } from '../programming-exercise.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';

/**
 * Checkbox to toggle an automatic submission run after the due date passes.
 */
@Component({
    selector: 'jhi-programming-exercise-due-date-select',
    template: `
        <jhi-date-time-picker
            labelName="{{ 'artemisApp.exercise.dueDate' | translate }}"
            [ngModel]="programmingExercise.dueDate"
            (ngModelChange)="updateDueDate($event)"
            name="dueDate"
        ></jhi-date-time-picker>
        <div id="automatic-submission-after-due-date" class="form-check ml-2" *ngIf="programmingExercise.dueDate && programmingExercise.dueDate.isValid()">
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
                <span jhiTranslate="artemisApp.programmingExercise.automaticSubmissionRunAfterDueDate.title">Automatic Submission Run After Due Date</span>
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
export class ProgrammingExerciseDueDateSelectComponent {
    @Input() programmingExercise: ProgrammingExercise;
    @Output() onProgrammingExerciseUpdate = new EventEmitter<ProgrammingExercise>();

    @HostBinding('class') class = 'form-group-narrow flex-grow-1 ml-3';
    @ViewChild(FormDateTimePickerComponent, { static: false }) dateTimePicker: FormDateTimePickerComponent;

    public updateDueDate(dueDate: string) {
        const updatedDueDate = moment(dueDate).isValid() ? moment(dueDate) : null;
        const updatedSubmissionRunDate = this.programmingExercise.automaticSubmissionRunDate && updatedDueDate && updatedDueDate.isValid() ? updatedDueDate.add(1, 'hours') : null;
        const updatedProgrammingExercise = { ...this.programmingExercise, dueDate: updatedDueDate, automaticSubmissionRunDate: updatedSubmissionRunDate };
        this.onProgrammingExerciseUpdate.emit(updatedProgrammingExercise);
    }

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
