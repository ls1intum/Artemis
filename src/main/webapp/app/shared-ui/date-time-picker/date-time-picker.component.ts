import { Component, computed, forwardRef, input, model, output, signal } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faClock, faGlobe, faQuestionCircle, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DatePickerModule } from 'primeng/datepicker';
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
    imports: [FaStackComponent, NgbTooltip, FaIconComponent, FaStackItemSizeDirective, FormsModule, DatePickerModule, TranslateDirective, ArtemisTranslatePipe],
})
export class FormDateTimePickerComponent implements ControlValueAccessor {
    protected readonly faGlobe = faGlobe;
    protected readonly faClock = faClock;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTriangleExclamation = faTriangleExclamation;

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
    baseZIndex = input<number>(1060); // z-index floor for the overlay panel so it renders above ng-bootstrap modals (~1055).
    valueChange = output<void>();

    protected isInputValid = signal<boolean>(true);
    protected dateInputValue = signal<string>('');

    /** DEFAULT renders date + time; CALENDAR renders date only; TIMER renders time only. */
    protected showTime = computed(() => this.pickerType() === DateTimePickerType.DEFAULT);
    protected timeOnly = computed(() => this.pickerType() === DateTimePickerType.TIMER);
    protected dateFormat = computed(() => (this.timeOnly() ? undefined : 'dd.mm.yy'));
    protected placeholder = computed(() => {
        switch (this.pickerType()) {
            case DateTimePickerType.TIMER:
                return 'hh:mm';
            case DateTimePickerType.CALENDAR:
                return 'dd.mm.yyyy';
            default:
                return 'dd.mm.yyyy hh:mm';
        }
    });

    isValid = computed(() => {
        const isInvalid = this.error() || !this.isInputValid() || (this.requiredField() && !this.dateInputValue()) || this.warning();
        return !isInvalid;
    });

    /**
     * Backwards-compatible accessor: a few consumers (e.g. the exercise-update components) read
     * `dateTimePicker.dateInput.valid` to gate overall form validity. We expose the input validity
     * through the same shape instead of a raw `NgModel`.
     */
    get dateInput(): { valid: boolean } {
        return { valid: this.isInputValid() };
    }

    private onChange?: (val?: dayjs.Dayjs) => void;

    /**
     * Emits the value change from component.
     */
    valueChanged() {
        this.valueChange.emit();
    }

    /**
     * Function that writes the value safely.
     * @param value as dayjs or date
     */
    writeValue(value: any) {
        // convert dayjs to date, because p-datepicker only works correctly with date objects
        const next = dayjs.isDayjs(value) ? (value as dayjs.Dayjs).toDate() : (value ?? null);
        // Idempotency guard: Angular re-invokes writeValue on every change-detection pass while
        // p-datepicker's CVA write calls markForCheck. Re-setting the `value` signal with an equal
        // value would never let change detection settle (NG0103), so skip no-op writes.
        if (this.valuesEqual(this.value(), next)) {
            return;
        }
        this.value.set(next);
        this.updateSignals();
    }

    /** True when both values represent the same instant (or are both empty). */
    private valuesEqual(a?: dayjs.Dayjs | Date | string | null, b?: dayjs.Dayjs | Date | string | null): boolean {
        const aEmpty = a == undefined;
        const bEmpty = b == undefined;
        if (aEmpty || bEmpty) {
            return aEmpty && bEmpty;
        }
        const da = dayjs(a);
        const db = dayjs(b);
        return da.isValid() && db.isValid() && da.isSame(db);
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
     * Handles model changes emitted by the p-datepicker.
     *
     * With `keepInvalid="true"` and the default `dataType="date"`, the picker emits:
     * - a `Date` for a valid selection / fully parsed entry,
     * - `null` when the field is cleared / empty,
     * - the raw, unparseable `string` while the user is typing an invalid date.
     *
     * The string case must never be converted to a dayjs (that would silently fabricate a date);
     * instead we keep the typed text visible (via `keepInvalid`) and only flag the field invalid.
     * @param newValue the value emitted by the picker
     */
    updateField(newValue: Date | string | null) {
        const currentValue = this.value();
        if (newValue instanceof Date && dayjs(newValue).isValid()) {
            // Always refresh validity (this also recovers from a previous unparseable entry).
            this.isInputValid.set(true);
            this.dateInputValue.set(newValue.toISOString());
            // Only propagate when the instant actually changed. Re-setting the bound `value` signal
            // with an equal date would feed an infinite change-detection loop (NG0103) when the form
            // value is patched programmatically and p-datepicker re-emits through its ngModel.
            const unchanged = (dayjs.isDayjs(currentValue) || currentValue instanceof Date) && dayjs(currentValue).isSame(dayjs(newValue));
            if (!unchanged) {
                this.value.set(newValue);
                this.onChange?.(dayjs(newValue));
            }
        } else if (newValue == undefined || newValue === '') {
            // Empty is valid-but-missing; the required check is handled separately by `isValid`.
            this.isInputValid.set(true);
            this.dateInputValue.set('');
            if (currentValue != undefined) {
                this.value.set(null);
                this.onChange?.(undefined);
            }
        } else {
            // leftover unparseable text: keep it visible in the input but flag invalid
            this.isInputValid.set(false);
            this.dateInputValue.set(String(newValue));
            this.onChange?.(undefined);
        }
        this.valueChanged();
    }

    /**
     * Recomputes the validity signals from the currently bound value. Called after `writeValue`,
     * and exposed publicly so parents can refresh validation after programmatically patching the
     * bound form value (e.g. `exam-update` does this after a CD cycle).
     */
    updateSignals() {
        const currentValue = this.value();
        const parsed = currentValue != undefined ? dayjs(currentValue) : undefined;
        // An empty field is valid (the required check is handled separately); a present-but-unparseable value is not.
        this.isInputValid.set(parsed == undefined || parsed.isValid());
        this.dateInputValue.set(parsed?.isValid() ? parsed.toISOString() : '');
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

    protected readonly DateTimePickerType = DateTimePickerType;
}
