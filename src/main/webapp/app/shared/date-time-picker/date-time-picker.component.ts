import { Component, forwardRef, Input, Output, EventEmitter } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import * as moment from 'moment';
import { Moment, isMoment } from 'moment';

@Component({
    selector: 'jhi-date-time-picker',
    template: `
        <label class="form-control-label" *ngIf="labelName">
            {{ labelName }}
        </label>
        <div class="d-flex">
            <input
                class="form-control position-relative pl-5"
                [ngClass]="{ 'is-invalid': error }"
                [ngModel]="value"
                [disabled]="disabled"
                (ngModelChange)="updateField($event)"
                [owlDateTime]="dt"
                name="datePicker"
            />
            <button [owlDateTimeTrigger]="dt" class="btn position-absolute" type="button">
                <fa-icon [icon]="'calendar-alt'"></fa-icon>
            </button>
            <owl-date-time #dt></owl-date-time>
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
    @Input() labelName: string;
    @Input() value: any;
    @Input() disabled: boolean;
    @Input() error: boolean;
    @Output() valueChange = new EventEmitter();

    _onChange = (val: Moment) => {};

    valueChanged() {
        this.valueChange.emit();
    }

    writeValue(value: any) {
        // convert moment to date, because owl-date-time only works correctly with date objects
        if (isMoment(value)) {
            this.value = (value as Moment).toDate();
        } else {
            this.value = value;
        }
    }
    registerOnTouched(fn: any) {}
    registerOnChange(fn: any) {
        this._onChange = fn;
    }
    updateField(newValue: Moment) {
        this.value = newValue;
        this._onChange(moment(this.value));
        this.valueChanged();
    }
}
