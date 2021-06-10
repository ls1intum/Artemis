import { Component, ElementRef, EventEmitter, forwardRef, Input, Output, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import * as moment from 'moment';
import { isMoment, Moment } from 'moment';

@Component({
    selector: 'jhi-date-time-picker',
    template: `
        <label class="form-control-label" *ngIf="labelName">
            {{ labelName }}
        </label>
        <div class="d-flex">
            <input
                #dateInput="ngModel"
                class="form-control position-relative ps-5"
                [ngClass]="{ 'is-invalid': error }"
                [ngModel]="value"
                [disabled]="disabled"
                [min]="min?.isValid() ? min.toDate() : null"
                [max]="max?.isValid() ? max.toDate() : null"
                (ngModelChange)="updateField($event)"
                [owlDateTime]="dt"
                name="datePicker"
            />
            <button [owlDateTimeTrigger]="dt" class="btn position-absolute" type="button">
                <fa-icon [icon]="'calendar-alt'"></fa-icon>
            </button>
            <owl-date-time [startAt]="startAt?.isValid() ? startAt.toDate() : null" #dt></owl-date-time>
        </div>
    `,
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: forwardRef(() => FormDateTimePickerComponent),
        },
    ],
})
export class FormDateTimePickerComponent implements ControlValueAccessor {
    @ViewChild('dateInput', { static: false }) dateInput: ElementRef;
    @Input() labelName: string;
    @Input() value: any;
    @Input() disabled: boolean;
    @Input() error: boolean;
    @Input() startAt: Moment = moment().startOf('minutes'); // Default selected date. By default this sets it to the current time without seconds or milliseconds;
    @Input() min: Moment; // Dates before this date are not selectable.
    @Input() max: Moment; // Dates after this date are not selectable.
    @Output() valueChange = new EventEmitter();

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (val: Moment) => {};

    /**
     * Emits the value change from component.
     */
    valueChanged() {
        this.valueChange.emit();
    }

    /**
     * Function that writes the value safely.
     * @param value as moment or date
     */
    writeValue(value: any) {
        // convert moment to date, because owl-date-time only works correctly with date objects
        if (isMoment(value)) {
            this.value = (value as Moment).toDate();
        } else {
            this.value = value;
        }
    }

    /**
     * Registers a callback function is called by the forms API on initialization to update the form model on blur.
     * @param fn
     */
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    registerOnTouched(fn: any) {}

    /**
     *
     * @param fn
     */
    registerOnChange(fn: any) {
        this._onChange = fn;
    }

    /**
     *
     * @param newValue
     */
    updateField(newValue: Moment) {
        this.value = newValue;
        this._onChange(moment(this.value));
        this.valueChanged();
    }
}
