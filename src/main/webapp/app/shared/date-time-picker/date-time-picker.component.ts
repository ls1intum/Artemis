import { Component, ViewChild, computed, forwardRef, input, model, output, signal } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, NgModel } from '@angular/forms';
import { faCalendarAlt, faCircleXmark, faClock, faGlobe, faQuestionCircle, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';

export enum DateTimePickerType {
    CALENDAR,
    TIMER,
    DEFAULT,
}

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
    imports: [
        FaStackComponent,
        NgbTooltip,
        FaIconComponent,
        FaStackItemSizeDirective,
        FormsModule,
        OwlDateTimeModule,
        NgClass,
        NgTemplateOutlet,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class FormDateTimePickerComponent implements ControlValueAccessor {
    protected readonly faCalendarAlt = faCalendarAlt;
    protected readonly faGlobe = faGlobe;
    protected readonly faClock = faClock;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faTriangleExclamation = faTriangleExclamation;

    @ViewChild('dateInput', { static: false }) dateInput: NgModel;

    labelName = input<string>();
    hideLabelName = input<boolean>(false);
    labelTooltip = input<string>();
    value = model<dayjs.Dayjs | Date | null>();
    disabled = input<boolean>(false);
    error = input<boolean>();
    warning = input<boolean>();
    requiredField = input<boolean>(false);
    startAt = input<dayjs.Dayjs | undefined>(); // Default selected date. By default, this sets it to the current time without seconds or milliseconds;
    min = input<dayjs.Dayjs>(); // Dates before this date are not selectable.
    max = input<dayjs.Dayjs>(); // Dates after this date are not selectable.
    shouldDisplayTimeZoneWarning = input<boolean>(true); // Displays a warning that the current time zone might differ from the participants'.
    pickerType = input<DateTimePickerType>(DateTimePickerType.DEFAULT); // Select type of picker
    valueChange = output<void>();

    protected isInputValid = signal<boolean>(false);
    protected dateInputValue = signal<string>('');

    isValid = computed(() => {
        const isInvalid = this.error() || !this.isInputValid() || (this.requiredField() && !this.dateInputValue()) || this.warning();
        return !isInvalid;
    });

    updateSignals(): void {
        this.isInputValid.set(!this.dateInput?.invalid);
        this.dateInputValue.set(this.dateInput?.value);
    }

    private onChange?: (val?: dayjs.Dayjs) => void;

    /**
     * Emits the value change from component.
     */
    valueChanged() {
        this.valueChange.emit();
        this.updateSignals();
    }

    /**
     * Function that writes the value safely.
     * @param value as dayjs or date
     */
    writeValue(value: any) {
        // convert dayjs to date, because owl-date-time only works correctly with date objects
        if (dayjs.isDayjs(value)) {
            this.value.set((value as dayjs.Dayjs).toDate());
        } else {
            this.value.set(value);
        }
        this.updateSignals();
    }

    /**
     * Registers a callback function is called by the forms API on initialization to update the form model on blur.
     * @param _fn
     */
    registerOnTouched(_fn: any) {}

    /**
     *
     * @param fn
     */
    registerOnChange(fn: any) {
        this.onChange = fn;
    }

    /**
     *
     * @param newValue
     */
    updateField(newValue: dayjs.Dayjs) {
        this.value.set(newValue);
        this.onChange?.(dayjs(this.value()));
        this.valueChanged();
    }

    /**
     * Get the current time zone of the user / browser
     */
    get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    startDate = computed(() => {
        return this.convertToDate(this.startAt?.() ?? dayjs().startOf('minutes'));
    });

    minDate = computed(() => {
        return this.convertToDate(this.min?.());
    });

    maxDate = computed(() => {
        return this.convertToDate(this.max?.());
    });

    /**
     * Function that converts a possibly undefined dayjs value to a date or null.
     *
     * @param value as dayjs
     */
    convertToDate(value?: dayjs.Dayjs) {
        return value != undefined && value.isValid() ? value.toDate() : null;
    }

    /**
     * Clear the datepicker value.
     */
    clearDate() {
        this.dateInput.reset(undefined);
        if (this.onChange) {
            this.onChange(undefined);
        }
        this.updateSignals();
    }

    protected readonly DateTimePickerType = DateTimePickerType;
}
