import { Component, ElementRef, EventEmitter, Input, Output, ViewChild, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faCalendarAlt, faCalendarMinus, faClock, faGlobe, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
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
    @Output() valueChange = new EventEmitter();

    // Icons
    faCalendarAlt = faCalendarAlt;
    faGlobe = faGlobe;
    faClock = faClock;
    faQuestionCircle = faQuestionCircle;
    faCalendarMinus = faCalendarMinus;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _onChange = (val: dayjs.Dayjs) => {};

    /**
     * Emits the value change from component.
     */
    valueChanged() {
        this.valueChange.emit();
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
    writeValue(value: any): void {
        if (value !== undefined && this.value !== value) {
            this.value = !value ? null : isDate(value) ? value : value.toDate();
            this.value?.setSeconds(0, 0);
            this._onChange(value);
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
     * updates the value
     * @param newValue used to update value
     */
    updateField(newValue: dayjs.Dayjs) {
        this.value = newValue;
        this._onChange(dayjs(this.value));
        this.valueChanged();
    }

    /**
     * Get the current time zone of the user / browser
     */
    get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    /**
     * resets the date to null
     */
    resetDate() {
        this.writeValue(null);
        this.valueChanged();
    }
}
