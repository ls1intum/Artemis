import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faCalendarAlt, faClock, faGlobe, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FormControl } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { isDate } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-date-time-picker',
    templateUrl: `./date-time-picker.component.html`,
    styleUrls: [`./date-time-picker.component.scss`],
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
    @Input() labelTooltip: string;
    @Input() value: any;
    @Input() disabled: boolean;
    @Input() error: boolean;
    @Input() startAt: dayjs.Dayjs = dayjs().startOf('minutes'); // Default selected date. By default this sets it to the current time without seconds or milliseconds;
    @Input() min: dayjs.Dayjs; // Dates before this date are not selectable.
    @Input() max: dayjs.Dayjs; // Dates after this date are not selectable.
    @Input() shouldDisplayTimeZoneWarning = true; // Displays a warning that the current time zone might differ from the participants'.
    @Output() valueChange: EventEmitter<boolean> = new EventEmitter();

    invalidDate = false;

    public nameControl = new FormControl();

    // Icons
    faCalendarAlt = faCalendarAlt;
    faGlobe = faGlobe;
    faClock = faClock;
    faQuestionCircle = faQuestionCircle;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (val: dayjs.Dayjs) => {};

    /**
     * Emits the value change from component.
     */
    valueChanged(invalidD: boolean) {
        this.valueChange.emit(invalidD);
    }

    /**
     * Function that converts a possibly undefined dayjs value to a date or null.
     *
     * @param value as dayjs
     */
    convert(value?: dayjs.Dayjs) {
        return value != undefined && value.isValid() ? value.toDate() : null;
    }

    /**
     * Function that writes the value safely.
     * @param value as dayjs or date
     */
    writeValue(value: any) {
        // convert dayjs to date, because owl-date-time only works correctly with date objects
        if (dayjs.isDayjs(value)) {
            this.value = (value as dayjs.Dayjs).toDate();
        } else {
            this.value = value;
        }
    }

    isValidDate(date: any) {
        return date && Object.prototype.toString.call(date) === '[object Date]' && !isNaN(date);
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
     * updates the value with the passed newValue. It is checked if the field is empty or the newValue is not null. Then
     * the passed date is valid, invalidDate can be set to false. Otherwise, the date is invalid.
     * @param newValue a valid date object or null
     * @param inputValue the string value of #datePicker, the field is empty when inputValue == ''
     */
    updateField(newValue: any, inputValue: string) {
        if (this.value != newValue && newValue != '') {
            console.log('newValue' + newValue + 'newValue');
            console.log('inputValue ' + inputValue);
            if (newValue != null || inputValue == '') {
                this.invalidDate = false;
            } else {
                this.invalidDate = true;
                console.log('invalid date!');
            }
            this.value = newValue;
            this._onChange(this.value);
            this.valueChanged(this.invalidDate);
            this.writeValue(this.value);
        } else {
            console.log('THE SAME');
        }
    }

    /**
     * Get the current time zone of the user / browser
     */
    get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    validate(event: Event) {
        const val = (event.target as HTMLInputElement).value;
        const date = new Date(val);

        if (val == '') {
            this.invalidDate = false;
            this.value = null;
        } else if (dayjs(val).isValid() && this.isValidDate(date)) {
            this.invalidDate = false;
            this.value = date;
        } else {
            this.invalidDate = true;
        }
        this._onChange(this.value);
        this.valueChanged(this.invalidDate);
    }

    emptyField(newValue: any, inputValue: string) {
        if (inputValue == '') {
            this.value = null;
            this.invalidDate = false;
            this._onChange(this.value);
            this.valueChanged(this.invalidDate);
        }
    }
}
