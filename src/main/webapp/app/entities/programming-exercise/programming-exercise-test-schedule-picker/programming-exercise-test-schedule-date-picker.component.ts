import { Component, ElementRef, EventEmitter, forwardRef, Input, OnInit, Output, ViewChild } from '@angular/core';
import { isMoment, Moment } from 'moment';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import * as moment from 'moment';

@Component({
    selector: 'jhi-programming-exercise-test-schedule-date-picker',
    template: `
        <div>
            <div class="font-weight-bold">
                {{ label | translate }}
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
            <button *ngIf="!value" [owlDateTimeTrigger]="dt" type="button" class="btn btn-light btn-circle">
                <fa-icon class="icon-calendar-plus" icon="calendar-plus" size="2x"></fa-icon>
            </button>
            <button *ngIf="value" (click)="resetDate()" type="button" class="btn btn-light btn-circle calendar-event-toggle">
                <fa-icon class="icon-static" icon="calendar-check" size="2x"></fa-icon>
                <fa-icon class="icon-remove" icon="calendar-minus" size="2x"></fa-icon>
            </button>
            <div *ngIf="value">
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
    @Input() value: any;
    @Input() startAt: Moment;
    @Input() min: Moment;
    @Input() max: Moment;
    @Input() label: String;
    @Output() onDateReset = new EventEmitter();

    _onChange = (val: Moment) => {};

    registerOnChange(fn: any): void {
        this._onChange = fn;
    }

    registerOnTouched(fn: any): void {}

    setDisabledState(isDisabled: boolean): void {}

    writeValue(obj: any): void {
        this.value = isMoment(obj) ? (obj as Moment).toDate() : obj;
    }

    updateField(newValue: Moment) {
        this.value = newValue;
        this._onChange(moment(this.value));
    }

    resetDate() {
        this.value = null;
        this.onDateReset.emit();
    }
}
