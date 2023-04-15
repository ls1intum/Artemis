import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faCalendarAlt, faClock, faGlobe, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';

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

    isInvalidDate = false;

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
    valueChanged() {
        this.valueChange.emit(this.isInvalidDate);
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

    /**
     * validates a date object
     * @param date to be validated
     */
    isValidDate(date: object) {
        return date && date instanceof Date && !Number.isNaN(date.getTime());
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
     * updates the value with the passed newValue date. The date picked using the calendar is always valid.
     * In case the calendar was opened and no date was selected, the value does not get updated
     * @param newValue the date picked with the date picker or another object in case an invalid value
     * is selected in the user input and the calendar was exited without saving
     */
    updateField(newValue: Date) {
        console.log(newValue);
        if (this.isValidDate(newValue)) {
            this.isInvalidDate = false;
            this.value = newValue;
            this._onChange(this.value);
            this.valueChanged();
        }
    }

    /**
     * Get the current time zone of the user / browser
     */
    get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    /**
     * validates the value from the input field, if it is a date value. In case it is invalid, isInvalidDate is set to true.
     * An empty input field is considered valid, in this case the value is assigned null
     * @param event the change event
     */
    validateAndUpdateField(event: Event) {
        const val = (event.target as HTMLInputElement).value;
        //value is empty string in case the input field has been cleared (this is handled in updateEmptyField)
        if (val != '') {
            //date is InvalidDate or an actual date
            const date = new Date(val);
            if (dayjs(val).isValid() && this.isValidDate(date)) {
                this.isInvalidDate = false;
                this.value = date;
            } else {
                this.isInvalidDate = true;
            }
            this._onChange(this.value);
            this.valueChanged();
        }
    }

    /**
     * directly updates the value (and removes the invalidDate error message) if the input field has been cleared
     * @param inputValue the string value from the input field
     */
    updateEmptyField(inputValue: string) {
        if (inputValue == '') {
            this.isInvalidDate = false;
            this.value = null;
            this._onChange(this.value);
            this.valueChanged();
        }
    }
}
