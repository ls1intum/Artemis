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
    @Input() value0: any;
    @Input() disabled: boolean;
    @Input() error: boolean;
    @Input() startAt: dayjs.Dayjs = dayjs().startOf('minutes'); // Default selected date. By default this sets it to the current time without seconds or milliseconds;
    @Input() min: dayjs.Dayjs; // Dates before this date are not selectable.
    @Input() max: dayjs.Dayjs; // Dates after this date are not selectable.
    @Input() shouldDisplayTimeZoneWarning = true; // Displays a warning that the current time zone might differ from the participants'.
    @Output() valueChange = new EventEmitter();
    @Output() invalidDate = new EventEmitter();

    invalidD = false;

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
    /*writeValue(value: dayjs.Dayjs | Date | null): void {
        if (value !== undefined && this.value !== value) {
            if (value == null) {
                this.value = null;
            } else {
                this.value = isDate(value) ? value : ((dayjs) value).toDate();
                this.value.setSeconds(0, 0);
            }
            this._onChange(value);
        } TODO: reuse old?
    }*/

    /**
     * Function that writes the value safely.
     * @param value as dayjs or date
     */
    writeValue(value: any) {
        // convert dayjs to date, because owl-date-time only works correctly with date objects
        if (dayjs.isDayjs(value)) {
            this.value0 = (value as dayjs.Dayjs).toDate();
        } else {
            this.value0 = value;
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
     * updates the value, if a valid Dayjs value is passed
     * @param newValue used to update value
     */
    updateField0(newValue: any) {
        if (dayjs.isDayjs(newValue) || isDate(newValue)) {
            this.invalidD = false;
            this.value0 = newValue.toDate();
            this._onChange(dayjs(this.value0));
            alert(this.value0);
        }
    }

    updateField(newValue: any, ok: string) {
        const str = (<HTMLInputElement>document.getElementById('date-input-field')).value;
        console.log(newValue);
        console.log('str: ' + ok);
        //todo: this does not work :(
        if (newValue != null || ok == '') {
            this.invalidD = false;
        } else {
            this.invalidD = true;
        }
        this.value0 = newValue;
        this._onChange(dayjs(this.value0));
        this.valueChanged();
    }

    /**
     * Get the current time zone of the user / browser
     */
    get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    updateFieldTest(newValue: Date) {
        if (newValue == null) {
            alert('new value is null :p');
        }

        this.value0 = newValue;
        this._onChange(dayjs(this.value0));
        this.valueChanged();
    }

    validate(event: Event) {
        const val = (event.target as HTMLInputElement).value;

        if (val == '') {
            this.invalidD = false;
            this.value0 = null;
            this._onChange(dayjs(this.value0));
            this.valueChanged();
        } else if (dayjs(val).isValid()) {
            this.invalidD = false;
            this.value0 = new Date(val);
            this._onChange(dayjs(this.value0));
            this.valueChanged();
        } else {
            this.invalidD = true;
        }
    }

    all() {
        const str = (<HTMLInputElement>document.getElementById('date-input-field')).value;
        alert('DEBUG: ' + str);
    }

    public myFilter = (d: any): boolean => {
        console.log(d);

        if (isDate(d)) {
            if (isNaN(d.getTime())) {
                alert('d is NAN');
                return false;
            }

            const day = d.getDay();
            // Prevent Saturday and Sunday from being selected.

            return day !== 0 && day !== 6;
        } else {
            return false;
        }
    };
}
