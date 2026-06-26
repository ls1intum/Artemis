import { Component, computed, forwardRef, input, model, output, viewChild } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, NgModel } from '@angular/forms';
import { faCalendarAlt, faCircleXmark, faClock, faGlobe, faQuestionCircle, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** Value shapes the picker accepts from the forms API: a dayjs instant, a native Date, an ISO string, or empty. */
type DateInput = dayjs.Dayjs | Date | string | null | undefined;

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
    imports: [FaStackComponent, TooltipModule, FaIconComponent, FaStackItemSizeDirective, FormsModule, DatePickerModule, TranslateDirective, ArtemisTranslatePipe],
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

    /**
     * The inner `NgModel`. Exposed because exercise/exam update forms read `dateInput.valid` to gate their
     * own submit state, and tests inject a stub via the setter. Not purely internal — keep public.
     */
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

    /**
     * True when the bound value is a present, valid date. Validity derives from the bound value, not from DOM
     * input events: a programmatically set `[value]`/`writeValue` (edit forms, the audits filter) never fires an
     * input event, so a DOM-driven check would mark a valid preset date invalid.
     */
    protected hasValidValue = computed(() => {
        const value = this.value();
        return value != undefined && (dayjs.isDayjs(value) ? value.isValid() : !isNaN(new Date(value).getTime()));
    });

    /**
     * True when a value is PRESENT but not a valid, in-range date — i.e. unparseable, or outside the picker's
     * `[min]`/`[max]` bounds. Mirrors what the inner p-datepicker `NgModel` reports invalid, but derived from the
     * value/min/max signals so it stays reactive and preset-safe (a programmatic in-range date never trips it; an
     * empty value is not "invalid" here — that case is the `requiredField()` check). Used by both `isValid()`
     * (external submit gating) and the template error message, so the two never disagree.
     */
    protected hasInvalidValue = computed(() => {
        const value = this.value();
        if (value == undefined) return false;
        if (!this.hasValidValue()) return true; // present but unparseable (even for an optional field)
        const date = dayjs(value);
        const min = this.min();
        const max = this.max();
        return Boolean((min?.isValid() && date.isBefore(min)) || (max?.isValid() && date.isAfter(max)));
    });

    isValid = computed(() => !(this.error() || this.warning() || (this.requiredField() && !this.hasValidValue()) || this.hasInvalidValue()));

    /**
     * Whether the underlying p-datepicker should render the time picker (hours/minutes).
     * CALENDAR -> date only; TIMER and DEFAULT -> with time.
     */
    protected showTime = computed(() => this.pickerType() !== DateTimePickerType.CALENDAR);

    /**
     * Whether the underlying p-datepicker should render the time picker only (no calendar grid).
     */
    protected timeOnly = computed(() => this.pickerType() === DateTimePickerType.TIMER);

    private onChange?: (value?: dayjs.Dayjs) => void;

    valueChanged() {
        this.valueChange.emit();
    }

    /**
     * The picker edits in the browser-LOCAL timezone and binds a native `Date`, while Artemis stores instants as
     * UTC dayjs values. Normalise any incoming value to a native `Date` for the same absolute instant so the
     * picker renders its local wall-clock.
     */
    writeValue(value: DateInput) {
        this.value.set(this.toLocalDate(value));
    }

    registerOnTouched(_fn: () => void) {}

    registerOnChange(fn: (value?: dayjs.Dayjs) => void) {
        this.onChange = fn;
    }

    /**
     * Called when the user picks (or clears) a value in the p-datepicker.
     *
     * p-datepicker emits a native `Date` interpreted as the LOCAL wall-clock the user sees, or
     * null/undefined when cleared. We convert that back to a dayjs that preserves the SAME absolute
     * instant via `dayjs(date)` (no offset is added or subtracted), so the UTC value persisted by
     * consumers matches exactly what the user picked — no off-by-timezone-offset drift. A `string`
     * passed programmatically is parsed by `dayjs` honouring any explicit zone it carries (e.g. a
     * trailing `Z`), so an absolute instant is never mis-read as local wall-clock.
     */
    updateField(newValue: DateInput) {
        this.value.set(this.toLocalDate(newValue));
        this.onChange?.(newValue != undefined ? dayjs(newValue) : undefined);
        this.valueChanged();
    }

    /**
     * Normalises any supported value shape to a native `Date` for the same absolute instant, or `undefined`/`null`
     * when there is no value. `dayjs(value)` keeps the instant for dayjs, Date and ISO-string inputs alike.
     */
    private toLocalDate(value: DateInput): Date | null | undefined {
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
        // Emit valueChange so consumers (e.g. the audits / finished-builds filters) revalidate on clear,
        // exactly as updateField() does for a normal edit.
        this.valueChanged();
    }

    protected readonly DateTimePickerType = DateTimePickerType;
}
