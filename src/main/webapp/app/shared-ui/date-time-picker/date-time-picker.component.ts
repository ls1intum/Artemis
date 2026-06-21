import { Component, computed, forwardRef, input, model, output, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, NgModel } from '@angular/forms';
import { faCalendarAlt, faCircleXmark, faClock, faGlobe, faQuestionCircle, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

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
    imports: [FaStackComponent, TooltipModule, FaIconComponent, FaStackItemSizeDirective, FormsModule, DatePickerModule, NgClass, TranslateDirective, ArtemisTranslatePipe],
})
export class FormDateTimePickerComponent implements ControlValueAccessor {
    protected readonly faCalendarAlt = faCalendarAlt;
    protected readonly faGlobe = faGlobe;
    protected readonly faClock = faClock;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faCircleXmark = faCircleXmark;
    protected readonly faTriangleExclamation = faTriangleExclamation;

    private readonly dateInputRef = viewChild<NgModel>('dateInput');
    private dateInputOverride?: NgModel;

    get dateInput(): NgModel {
        return this.dateInputOverride ?? this.dateInputRef()!;
    }

    set dateInput(dateInput: NgModel | undefined) {
        this.dateInputOverride = dateInput;
    }

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

    /**
     * True when the bound value is a present, valid date (dayjs or native Date). Source of truth for both the
     * invalid border and the error message — NOT DOM input events, which never fire for a programmatically set
     * [value]/writeValue (e.g. the audits filters, or any edit form opened with an existing date). Using the
     * DOM value made a valid programmatic date read as invalid (red border) and spuriously rendered the
     * "date is missing" message, which expanded the field.
     */
    protected hasValidValue = computed(() => {
        const value = this.value();
        return value != undefined && (dayjs.isDayjs(value) ? value.isValid() : !isNaN(new Date(value).getTime()));
    });

    isValid = computed(() => !(this.error() || this.warning() || (this.requiredField() && !this.hasValidValue())));

    /**
     * Whether the underlying p-datepicker should render the time picker (hours/minutes).
     * CALENDAR -> date only; TIMER and DEFAULT -> with time.
     */
    protected showTime = computed(() => this.pickerType() !== DateTimePickerType.CALENDAR);

    /**
     * Whether the underlying p-datepicker should render the time picker only (no calendar grid).
     */
    protected timeOnly = computed(() => this.pickerType() === DateTimePickerType.TIMER);

    updateSignals(): void {
        const dateInput = this.dateInputRef() ?? this.dateInputOverride;
        this.isInputValid.set(!dateInput?.invalid);
        this.dateInputValue.set(dateInput?.value);
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
     *
     * The picker (p-datepicker) edits in the browser-LOCAL timezone and binds a native `Date`.
     * Artemis stores instants as UTC dayjs values. We therefore normalise any incoming value to a
     * native `Date` that represents the same absolute instant — p-datepicker then renders the
     * local wall-clock of that instant (identical behaviour to the previous owl-date-time picker).
     *
     * @param value as dayjs, native Date, ISO string (e.g. typed by the e2e helper), null or undefined
     */
    writeValue(value: any) {
        this.value.set(this.toLocalDate(value));
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
     * Called when the user picks (or clears, or types) a value in the p-datepicker.
     *
     * p-datepicker emits a native `Date` interpreted as the LOCAL wall-clock the user sees, or
     * null/undefined when cleared. We convert that back to a dayjs that preserves the SAME absolute
     * instant via `dayjs(date)` (no offset is added or subtracted), so the UTC value persisted by
     * consumers matches exactly what the user picked — no off-by-timezone-offset drift.
     *
     * A `string` can also reach this handler (e.g. the Playwright `enterDate` helper types a UTC ISO
     * string with a `Z` suffix); `dayjs(string)` parses the explicit zone correctly, so we never
     * mis-interpret an absolute instant as local wall-clock.
     *
     * @param newValue the value emitted by the picker (Date | dayjs | string | null | undefined)
     */
    updateField(newValue: dayjs.Dayjs | Date | string | null | undefined) {
        this.value.set(this.toLocalDate(newValue));
        this.onChange?.(newValue != undefined ? dayjs(newValue) : undefined);
        this.valueChanged();
    }

    /**
     * Normalises any supported value shape to a native `Date` (the type p-datepicker binds to) that
     * represents the same absolute instant, or `undefined`/`null` when there is no value.
     *
     * `dayjs(value)` keeps the absolute instant for dayjs, Date and ISO-string inputs alike, and
     * `.toDate()` hands p-datepicker a native Date whose local wall-clock is the instant's local
     * representation — the exact round-trip the old owl picker performed.
     */
    private toLocalDate(value: any): Date | null | undefined {
        if (value == undefined) {
            return value;
        }
        const parsed = dayjs(value);
        return parsed.isValid() ? parsed.toDate() : null;
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
        this.dateInput?.reset(undefined);
        this.value.set(undefined);
        if (this.onChange) {
            this.onChange(undefined);
        }
        this.updateSignals();
    }

    protected readonly DateTimePickerType = DateTimePickerType;
}
