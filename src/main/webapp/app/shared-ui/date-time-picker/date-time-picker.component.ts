import { AfterViewInit, Component, computed, forwardRef, input, model, output, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { faClock, faQuestionCircle, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DatePicker, DatePickerModule } from 'primeng/datepicker';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TimeZoneWarningComponent } from 'app/shared-ui/date-time-picker/time-zone-warning.component';

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
        DatePickerModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        TimeZoneWarningComponent,
    ],
})
export class FormDateTimePickerComponent implements ControlValueAccessor, AfterViewInit {
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
    // True when the last emission to the parent was onChange(undefined) due to an invalid or
    // out-of-range entry, but this.value() was NOT cleared (to preserve the display via
    // keepInvalid). Without this flag the `unchanged` guard in updateField would swallow the
    // re-emission when the user corrects the input back to the previously held date.
    private needsParentSync = false;

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
     * Whether the field should render the red "invalid" border. Mirrors the conditions that show the
     * "missing/invalid" message (unparseable / out-of-range / empty-required) plus the parent-provided
     * {@link error} flag, but excludes the (yellow) {@link warning} state, which has its own styling.
     *
     * Drives BOTH the inner `<p-datepicker [invalid]>` input and a class on the wrapper element (see the
     * template) so the two never disagree. The wrapper class is the load-bearing one: under zoneless
     * change detection the OnPush picker view can stay stale when validity flips as a result of the
     * picker's own `ngModelChange` (the message, rendered by this wrapper, updates but the picker's
     * border does not — see PR #13009 review). Driving the border from a wrapper class lets plain CSS
     * cascade onto the (existing) input element, so the border always matches the message regardless of
     * the inner picker's change-detection timing.
     */
    protected showErrorBorder = computed(() => !!this.error() || !this.isInputValid() || (this.requiredField() && !this.dateInputValue()));

    /**
     * Backwards-compatible accessor: a few consumers (e.g. the exercise-update components) read
     * `dateTimePicker.dateInput.valid` to gate overall form validity. We expose the input validity
     * through the same shape instead of a raw `NgModel`.
     */
    get dateInput(): { valid: boolean } {
        return { valid: this.isInputValid() && !(this.requiredField() && !this.dateInputValue()) };
    }

    private onChange?: (val?: dayjs.Dayjs) => void;

    private readonly innerPicker = viewChild(DatePicker);

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
            // The bound value is unchanged, but a prior unparseable entry may have left the validity
            // signals stale; refresh them so a programmatic reset/write clears any lingering invalid state.
            this.updateSignals();
            return;
        }
        this.value.set(next);
        this.updateSignals();
        this.reflectValueInPicker(next);
    }

    /**
     * Imperatively push a programmatically-written value into the inner p-datepicker.
     *
     * The inner `[ngModel]="value()"` one-way binding does NOT reliably update the OnPush p-datepicker
     * when this wrapper lives inside an OnPush parent under zoneless change detection: the parent is not
     * re-checked after `writeValue`, so an edit form opens with the picker blank (PR #13009 review — the
     * tutorial free-period form). This only runs on the programmatic (form patch / reset) path; user
     * typing flows through `updateField` and must NOT be reformatted here (it would erase keepInvalid text).
     */
    private reflectValueInPicker(next: Date | null) {
        this.innerPicker()?.writeControlValue(next);
    }

    /**
     * The inner picker's viewChild is not yet resolved during the initial `writeValue` (which runs while
     * the form control is wired up), so push the already-written value once the view exists. This is what
     * makes edit forms that are created with a value (e.g. each tutorial free-period tab) show it.
     */
    ngAfterViewInit() {
        const current = this.value();
        if (current != undefined) {
            this.reflectValueInPicker(this.convertToDate(dayjs(current)));
        }
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
            const parsed = dayjs(newValue);
            const min = this.min();
            const max = this.max();

            // P2 fix: PrimeNG emits a real Date even for dates outside [minDate]/[maxDate]
            // because the calendar popup's disabled-day range only prevents picking — typed input
            // bypasses it. Reject out-of-range values here so the parent model never receives them
            // and the field shows as invalid. We do NOT clear this.value() so the displayed date
            // stays visible (keepInvalid-like); needsParentSync ensures recovery is propagated.
            if ((min && parsed.isBefore(min)) || (max && parsed.isAfter(max))) {
                this.isInputValid.set(false);
                this.dateInputValue.set(newValue.toISOString());
                this.needsParentSync = true;
                this.onChange?.(undefined);
                this.valueChanged();
                return;
            }

            // Always refresh validity (this also recovers from a previous unparseable entry).
            this.isInputValid.set(true);
            this.dateInputValue.set(newValue.toISOString());

            // Only propagate when the instant actually changed. Re-setting the bound `value` signal
            // with an equal date would feed an infinite change-detection loop (NG0103) when the form
            // value is patched programmatically and p-datepicker re-emits through its ngModel.
            // P1 fix: also bypass the guard when needsParentSync is true — the parent model was set
            // to undefined by a prior invalid/out-of-range entry while this.value() still holds the
            // old date, so we must always re-emit to re-sync even if the date looks unchanged.
            const unchanged = !this.needsParentSync && (dayjs.isDayjs(currentValue) || currentValue instanceof Date) && dayjs(currentValue).isSame(parsed);
            if (!unchanged) {
                this.needsParentSync = false;
                this.value.set(newValue);
                this.onChange?.(dayjs(newValue));
            }
        } else if (newValue == undefined || newValue === '') {
            // Empty is valid-but-missing; the required check is handled separately by `isValid`.
            this.isInputValid.set(true);
            this.dateInputValue.set('');
            this.needsParentSync = false;
            if (currentValue != undefined) {
                this.value.set(null);
                this.onChange?.(undefined);
            }
        } else {
            // Unparseable text: keep it visible (keepInvalid) and flag the field invalid.
            // We do NOT clear this.value() here because that would update [ngModel] on the inner
            // p-datepicker and immediately erase the raw text the user just typed. Instead we set
            // needsParentSync so the unchanged guard (above) does not swallow the re-emission
            // when the user corrects the input back to the previously-held valid date.
            this.isInputValid.set(false);
            this.dateInputValue.set(String(newValue));
            this.needsParentSync = true;
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
     * p-datepicker accepts a valid *prefix* and silently ignores trailing characters, so
     * "13.06.2026 18:30adasdasdsad" parses to 13.06.2026 18:30 and looks valid (PR #13009 review).
     * On blur, reject input whose full text does not match the field's display format so such entries
     * are flagged instead of accepted. Time-only pickers keep PrimeNG's lenient parsing (decided
     * separately in review), and empty input is handled as valid-but-missing elsewhere.
     */
    onInputBlur(event: Event) {
        if (this.timeOnly()) {
            return;
        }
        const raw = (event.target as HTMLInputElement).value?.trim() ?? '';
        if (raw === '') {
            return;
        }
        const fullPattern = this.showTime() ? /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/ : /^\d{2}\.\d{2}\.\d{4}$/;
        if (!fullPattern.test(raw)) {
            this.isInputValid.set(false);
            this.dateInputValue.set(raw);
            this.needsParentSync = true;
            this.onChange?.(undefined);
            this.valueChanged();
        }
    }

    /**
     * Confirm button for the time picker. A time-only picker shows the default time (startAt / current
     * time) in its spinner but does not write it to the model until the user nudges a spinner field, so
     * applying the shown time previously took two clicks (nudge + close). When the field is still empty,
     * commit the displayed time here so a single click applies it; otherwise just close (PR #13009 review).
     */
    applyAndClose(picker: DatePicker) {
        if (this.timeOnly() && this.value() == undefined) {
            this.updateField(this.startDate());
        }
        picker.hideOverlay();
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
