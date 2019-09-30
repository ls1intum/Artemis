import { Component, ElementRef, EventEmitter, forwardRef, Input, OnInit, Output, ViewChild } from '@angular/core';
import { isMoment, Moment } from 'moment';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
    selector: 'jhi-programming-exercise-test-schedule-date-picker',
    template: `
        <div>
            <div class="font-weight-bold">
                {{ label }}
            </div>
            <div class="invisible-date-time-picker">
                <input
                    #dateInput="ngModel"
                    class="form-control"
                    [ngModel]="value"
                    [min]="min?.isValid() ? min.toDate() : null"
                    [max]="max?.isValid() ? max.toDate() : null"
                    (ngModelChange)="updateField($event)"
                    [owlDateTime]="dt"
                />
            </div>
            <button [owlDateTimeTrigger]="dt" type="button" data-toggle="tooltip" data-placement="bottom" title="Release Date" class="btn btn-light btn-circle">
                <fa-icon icon="calendar-plus" size="2x"></fa-icon>
            </button>
            <div>
                {{ value | date: 'EEE dd' }}<br />
                {{ value | date: 'HH:mm' }}
            </div>
            <owl-date-time [startAt]="startAt?.isValid() ? startAt.toDate() : null" #dt></owl-date-time>
        </div>
    `,
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: forwardRef(() => ProgrammingExerciseTestScheduleDatePickerComponent),
        },
    ],
    styleUrls: ['./programming-exercise-test-schedule-picker.scss'],
})
export class ProgrammingExerciseTestScheduleDatePickerComponent implements ControlValueAccessor {
    @ViewChild('dateInput', { static: false }) dateInput: ElementRef;
    @Input() value: Moment;
    @Input() startAt: Moment;
    @Input() min: Moment;
    @Input() max: Moment;
    @Input() label: String;
    @Output() valueChange = new EventEmitter();

    constructor() {}

    registerOnChange(fn: any): void {}

    registerOnTouched(fn: any): void {}

    setDisabledState(isDisabled: boolean): void {}

    writeValue(obj: Moment): void {
        this.value = obj;
    }

    updateField(newValue: Moment) {
        this.value = newValue;
        this.valueChange.emit();
    }
}
