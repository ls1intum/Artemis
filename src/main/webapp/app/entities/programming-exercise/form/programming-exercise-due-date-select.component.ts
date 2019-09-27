import * as moment from 'moment';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { ProgrammingExercise } from '../programming-exercise.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { hasExerciseChanged } from 'app/entities/exercise';

/**
 * Due date select for programming exercises.
 * When a due date is set, a checkbox appears that allows the activation of an automatic submission run after the due date passes.
 */
@Component({
    selector: 'jhi-programming-exercise-due-date-select',
    template: `
        <jhi-date-time-picker
            labelName="{{ 'artemisApp.exercise.dueDate' | translate }}"
            [ngModel]="exercise.dueDate"
            (ngModelChange)="updateDueDate($event)"
            name="dueDate"
        ></jhi-date-time-picker>
        <div id="build-and-test-date-container" class="form-check mt-1 d-flex">
            <label class="form-check-label flex-grow-1" for="field_buildAndTestStudentSubmissionsAfterDueDate">
                <div class="flex-grow-1 d-flex mt-1">
                    <input
                        class="form-check-input"
                        type="checkbox"
                        name="buildAndTestStudentSubmissionsAfterDueDate"
                        id="field_buildAndTestStudentSubmissionsAfterDueDate"
                        [disabled]="!exercise.dueDate || !exercise.dueDate.isValid()"
                        [ngModel]="buildAndTestDateActive"
                        (ngModelChange)="toggleBuildAndTestStudentSubmissionsAfterDueDate()"
                        checked
                    />
                    <span jhiTranslate="artemisApp.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate.title">Automatic Submission Run After Due Date</span>
                    <fa-icon
                        icon="question-circle"
                        class="text-secondary ml-1"
                        placement="top"
                        ngbTooltip="{{ 'artemisApp.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate.description' | translate }}"
                    ></fa-icon>
                </div>
                <div class="d-flex flex-column mt-1">
                    <jhi-date-time-picker
                        *ngIf="exercise.buildAndTestStudentSubmissionsAfterDueDate"
                        [ngModel]="exercise.buildAndTestStudentSubmissionsAfterDueDate"
                        (ngModelChange)="setBuildAndTestStudentSubmissionsAfterDueDate($event)"
                        (validationStateChange)="buildAndTestDateInvalid = $event"
                        [startAt]="exercise.dueDate"
                        [min]="exercise.dueDate"
                        name="buildAndTestStudentSumissionsAfterDueDate"
                    ></jhi-date-time-picker>
                    <div *ngIf="exercise.dueDate && exercise.dueDate.isValid() && buildAndTestDateInvalid" class="alert alert-danger">
                        <span [jhiTranslate]="'artemisApp.programmingExercise.buildAndTestStudentSubmissionsAfterDueDate.invalid'"></span>
                    </div>
                </div>
            </label>
        </div>
    `,
})
export class ProgrammingExerciseDueDateSelectComponent implements OnChanges {
    @Input() exercise: ProgrammingExercise;
    @Output() onProgrammingExerciseUpdate = new EventEmitter<ProgrammingExercise>();
    // true => Form is valid.
    @Output() onDueDateValidationChange = new EventEmitter<boolean>();

    @ViewChild(FormDateTimePickerComponent, { static: false }) dateTimePicker: FormDateTimePickerComponent;

    buildAndTestDateActive: boolean;
    buildAndTestDateInvalidValue = false;

    get buildAndTestDateInvalid() {
        return this.buildAndTestDateInvalidValue;
    }

    set buildAndTestDateInvalid(invalid: boolean) {
        this.buildAndTestDateInvalidValue = invalid;
        this.onDueDateValidationChange.emit(!invalid);
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (hasExerciseChanged(changes)) {
            this.buildAndTestDateActive = !!this.exercise.buildAndTestStudentSubmissionsAfterDueDate;
        }
    }

    /**
     * Set the due date. When the due date is set it needs to be checked if the automatic submission date is also set - this should then be updated, too.
     * @param dueDate of the programming exercise.
     */
    public updateDueDate(dueDate: moment.Moment | null) {
        const updatedProgrammingExercise = {
            ...this.exercise,
            dueDate: dueDate && dueDate.isValid() ? dueDate : null,
            buildAndTestStudentSubmissionsAfterDueDate: dueDate ? this.exercise.buildAndTestStudentSubmissionsAfterDueDate : null,
        };
        if (!dueDate) {
            this.buildAndTestDateActive = false;
        }
        this.buildAndTestDateInvalid =
            (this.exercise.buildAndTestStudentSubmissionsAfterDueDate && (!dueDate || !dueDate.isValid())) ||
            (this.buildAndTestDateActive && !this.exercise.buildAndTestStudentSubmissionsAfterDueDate) ||
            (!!this.exercise.buildAndTestStudentSubmissionsAfterDueDate &&
                !!dueDate &&
                dueDate.isValid() &&
                dueDate.isAfter(this.exercise.buildAndTestStudentSubmissionsAfterDueDate));
        this.onProgrammingExerciseUpdate.emit(updatedProgrammingExercise);
    }

    /**
     * When the buildAndTestAfterDueDate is enabled, we set its date to the due date by default. If it's disabled we set it to null.
     * The method will return immediately if there is no dueDate set.
     *
     * Will emit the updated programming exercise.
     */
    public toggleBuildAndTestStudentSubmissionsAfterDueDate() {
        if (!this.exercise.dueDate) {
            return;
        }
        this.buildAndTestDateActive = !this.buildAndTestDateActive;
        const updatedProgrammingExercise = {
            ...this.exercise,
            buildAndTestStudentSubmissionsAfterDueDate: !this.buildAndTestDateActive ? null : this.exercise.dueDate.clone(),
        };
        if (!this.buildAndTestDateActive || !!updatedProgrammingExercise.buildAndTestStudentSubmissionsAfterDueDate) {
            this.buildAndTestDateInvalid = false;
        }
        this.onProgrammingExerciseUpdate.emit(updatedProgrammingExercise);
    }

    /**
     * Set the buildAndTest date. Method will return immediately if no dueDate is set.
     * Will show an error when the set date is invalid.
     *
     * @param buildDate date string set by the date picker.
     */
    public setBuildAndTestStudentSubmissionsAfterDueDate(buildDate: string) {
        if (!this.exercise.dueDate) {
            return;
        }
        const buildDateMoment = moment(buildDate);
        this.buildAndTestDateInvalid = !buildDateMoment.isValid();
        const updatedProgrammingExercise = {
            ...this.exercise,
            buildAndTestStudentSubmissionsAfterDueDate: buildDateMoment.isValid() ? buildDateMoment : this.exercise.dueDate,
        };
        this.onProgrammingExerciseUpdate.emit(updatedProgrammingExercise);
    }
}
