import { Component, ElementRef, EventEmitter, forwardRef, Input, Output, ViewChild } from '@angular/core';
import { isDate, Moment } from 'moment';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
    selector: 'jhi-programming-exercise-test-schedule-date-picker',
    template: `
        <div>
            <div>
                <span class="font-weight-bold" [jhiTranslate]="label"></span>
                <jhi-help-icon *ngIf="tooltipText" placement="top" [text]="tooltipText"></jhi-help-icon>
            </div>
            <div class="invisible-date-time-picker">
                <input class="form-control" [ngModel]="val" [min]="min" [max]="max" (ngModelChange)="updateField($event)" [owlDateTime]="dt" />
            </div>
            <button *ngIf="!val" [owlDateTimeTrigger]="dt" type="button" class="btn btn-light btn-lifecycle">
                <fa-icon class="icon-calendar-plus" icon="calendar-plus" size="2x"></fa-icon>
            </button>
            <button *ngIf="val" (click)="resetDate()" type="button" class="btn btn-light btn-lifecycle calendar-event-toggle">
                <fa-icon class="icon-static" icon="calendar-check" size="2x"></fa-icon>
                <fa-icon class="icon-remove" icon="calendar-minus" size="2x"></fa-icon>
            </button>
            <div *ngIf="val">
                {{ val | date: 'MMM, dd' }}<br />
                {{ val | date: 'HH:mm' }}
            </div>
            <owl-date-time [startAt]="startAt" #dt></owl-date-time>
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
    @Input() val: Date | null;
    @Input() startAt: Moment | null;
    @Input() min: Moment;
    @Input() max: Moment;
    @Input() label: string;
    @Input() tooltipText: string;
    @Output() onDateReset = new EventEmitter();

    _onChange: any = () => {};

    set value(val: Moment | null) {
        if (val !== undefined && this.val !== val) {
            this.val = !val ? null : isDate(val) ? val : val.toDate();
            this._onChange(val);
        }
    }

    registerOnChange(fn: any): void {
        this._onChange = fn;
    }

    registerOnTouched(fn: any): void {}

    setDisabledState(isDisabled: boolean): void {}

    writeValue(obj: any): void {
        this.value = obj;
    }

    /**
     * Updates the current modal using the selected value from the date picker
     *
     * @param newValue The new value selected by the user
     */
    updateField(newValue: Moment) {
        this.value = newValue;
    }

    /**
     * Resets the date to null and informs parent components that the date has been reset.
     * This makes it easier to also reset date, that can only be selected if the current date is not null
     */
    resetDate() {
        this.value = null;
        this.onDateReset.emit();
    }
}
