import * as moment from 'moment';
import { Component, EventEmitter, Input, Output, HostBinding, ViewChild } from '@angular/core';
import { ProgrammingExercise } from '../programming-exercise.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';

/**
 * Due date select for programming exercises.
 * When a due date is set, a checkbox appears that allows the activation of an automatic submission run after the due date passes.
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
        <div id="automatic-submission-after-due-date" class="form-check mt-1" *ngIf="programmingExercise.dueDate && programmingExercise.dueDate.isValid()">
            <label class="form-check-label" for="field_buildAndTestStudentSubmissionsAfterDueDate">
                <input
                    class="form-check-input"
                    type="checkbox"
                    name="buildAndTestStudentSubmissionsAfterDueDate"
                    id="field_buildAndTestStudentSubmissionsAfterDueDate"
                    [disabled]="!programmingExercise.dueDate"
                    [ngModel]="!!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate"
                    (ngModelChange)="toggleBuildAndTestStudentSubmissionsAfterDueDate()"
                    checked
                />
                <span jhiTranslate="artemisApp.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate.title">Automatic Submission Run After Due Date</span>
                <fa-icon
                    icon="question-circle"
                    class="text-secondary"
                    placement="top"
                    ngbTooltip="{{ 'artemisApp.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate.description' | translate }}"
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

    /**
     * Set the due date. When the due date is set it needs to be checked if the automatic submission date is also set - this should then be updated, too.
     * @param dueDate of the programming exercise.
     */
    public updateDueDate(dueDate: string) {
        const updatedDueDate = moment(dueDate).isValid() ? moment(dueDate) : null;
        const updatedSubmissionRunDate =
            this.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate && updatedDueDate && updatedDueDate.isValid() ? updatedDueDate.clone().add(1, 'hours') : null;
        const updatedProgrammingExercise = { ...this.programmingExercise, dueDate: updatedDueDate, buildAndTestStudentSubmissionsAfterDueDate: updatedSubmissionRunDate };
        this.onProgrammingExerciseUpdate.emit(updatedProgrammingExercise);
    }

    /**
     * We currently don't allow the free setting of the automatic submission run date setting, but set it to one hour after the due date.
     * The method will return immediately if there is no dueDate set.
     *
     * Will emit the updated programming exercise.
     */
    public toggleBuildAndTestStudentSubmissionsAfterDueDate() {
        if (!this.programmingExercise.dueDate) {
            return;
        }
        const updatedProgrammingExercise = {
            ...this.programmingExercise,
            buildAndTestStudentSubmissionsAfterDueDate: this.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate
                ? null
                : this.programmingExercise.dueDate.clone().add(1, 'hours'),
        };
        this.onProgrammingExerciseUpdate.emit(updatedProgrammingExercise);
    }
}
