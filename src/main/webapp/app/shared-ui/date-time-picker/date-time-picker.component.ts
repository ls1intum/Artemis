import { AfterViewInit, Component, computed, effect, forwardRef, input, output, signal, untracked, viewChild } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator } from '@angular/forms';
import { faClock, faGlobe, faLock, faQuestionCircle, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
// TooltipModule remains for the still-PrimeNG `pTooltip`s on the label / timezone / visible-date hints; the
// variant-group lock overlay uses the tum-ui kit tooltip.
import { TooltipModule } from 'primeng/tooltip';
import { ButtonModule } from 'primeng/button';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import { DatePicker, DatePickerModule } from 'primeng/datepicker';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export enum DateTimePickerType {
    CALENDAR,
    TIMER,
    DEFAULT,
}

@Component({
    selector: 'jhi-date-time-picker',
    templateUrl: './date-time-picker.component.html',
    styleUrls: ['./date-time-picker.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: forwardRef(() => FormDateTimePickerComponent),
        },
        {
            provide: NG_VALIDATORS,
            multi: true,
            useExisting: forwardRef(() => FormDateTimePickerComponent),
        },
    ],
    imports: [
        FaStackComponent,
        TooltipModule,
        TumUiTooltipDirective,
        ButtonModule,
        FaIconComponent,
        FaStackItemSizeDirective,
        FormsModule,
        DatePickerModule,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
})
export class FormDateTimePickerComponent implements ControlValueAccessor, Validator, AfterViewInit {
    protected readonly faGlobe = faGlobe;
    protected readonly faClock = faClock;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faLock = faLock;

    /**
     * Names the parts of the PrimeNG picker the end-to-end tests reach for. Declared once rather than as a
     * template literal so change detection does not hand the picker a fresh object on every cycle.
     */
    protected readonly passThrough = {
        root: { 'data-testid': 'date-picker' },
        panel: { 'data-testid': 'date-picker-panel' },
        title: { 'data-testid': 'date-picker-title' },
        timePicker: { 'data-testid': 'date-picker-time-picker' },
        weekDay: { 'data-testid': 'date-picker-weekday' },
        day: { 'data-testid': 'date-picker-day' },
    };

    labelName = input<string>();
    hideLabelName = input<boolean>(false);
    // Suppress the inline "missing/invalid" message. Filters (e.g. the audits from/to range) convey invalid
    // input through the red border alone and must not grow taller when it appears; the invalid border still shows.
    hideValidationMessage = input<boolean>(false);
    // Id of the inner input, so a consumer can pair its own <label for> and keep ids unique when several
    // pickers share a page (e.g. the audits from/to filter).
    inputId = input<string>('date-input-field');
    labelTooltip = input<string>();
    // Internal CVA value holder. Not a public input/model: consumers bind the value via the
    // ControlValueAccessor (formControlName / ngModel), never via [value]/[(value)]. Keeping it a
    // plain signal avoids the model's implicit `valueChange` output colliding with the explicit
    // `valueChange` notification below (Angular 22 NG1054).
    value = signal<dayjs.Dayjs | Date | null | undefined>(undefined);
    disabled = input<boolean>(false);
    /**
     * Marks the field read-only because a variant group governs its value: editing is disabled, a lock icon shows, and
     * clicking emits {@link lockedClick} instead of opening the picker.
     */
    lockedToGroup = input<boolean>(false);
    /** Emitted when the user clicks a {@link lockedToGroup} field. */
    lockedClick = output<void>();
    error = input<boolean>();
    warning = input<boolean>();
    requiredField = input<boolean>(false);
    startAt = input<dayjs.Dayjs | undefined>(); // Default selected date. By default, this sets it to the current time without seconds or milliseconds;
    min = input<dayjs.Dayjs>(); // Dates before this date are not selectable.
    max = input<dayjs.Dayjs>(); // Dates after this date are not selectable.
    shouldDisplayTimeZoneWarning = input<boolean>(true); // Displays a warning that the current time zone might differ from the participants'.
    pickerType = input<DateTimePickerType>(DateTimePickerType.DEFAULT); // Select type of picker
    fluid = input(true);
    baseZIndex = input<number>(1060); // z-index floor for the overlay panel so it renders above ng-bootstrap modals (~1055).
    valueChange = output<void>();

    protected isInputValid = signal<boolean>(true);
    protected dateInputValue = signal<string>('');
    // True when the last emission to the parent was onChange(undefined) due to an invalid or
    // out-of-range entry, but this.value() was NOT cleared (to preserve the display via
    // keepInvalid). Without this flag the `unchanged` guard in updateField would swallow the
    // re-emission when the user corrects the input back to the previously held date.
    private needsParentSync = false;
    // A parseable date the range rejected, kept so a bound that later moves to include it can accept the entry
    // instead of making the user retype it. Undefined whenever the field holds anything else, in particular
    // unparseable text, which must never be resurrected as a date.
    private rejectedEntry?: dayjs.Dayjs;
    // Set by onPickerKeydown when Ctrl/Cmd+V is detected; cleared in onPickerPaste.
    // Avoids relying on PrimeNG's picker.isKeydown, which is set by ANY keydown (arrow, Home/End…)
    // and would remain stale-true when a context-menu paste follows a non-paste keydown.
    private isKeyboardPaste = false;

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

    /** Disabled either explicitly via {@link disabled} or because the value is governed by the variant group. */
    readonly effectiveDisabled = computed(() => this.disabled() || this.lockedToGroup());

    /**
     * Whether the field should render the red "invalid" border. Mirrors the conditions that show the
     * "missing/invalid" message (unparseable / out-of-range / empty-required) plus the parent-provided
     * {@link error} flag, but excludes the (yellow) {@link warning} state, which has its own styling.
     *
     * Drives BOTH the inner `<p-datepicker [invalid]>` input and a class on the wrapper element (see the
     * template) so the two never disagree. The wrapper class is the load-bearing one: under zoneless
     * change detection the OnPush picker view can stay stale when validity flips as a result of the
     * picker's own `ngModelChange` (the message, rendered by this wrapper, updates but the picker's
     * border does not). Driving the border from a wrapper class lets plain CSS
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
    private onValidatorChange?: () => void;

    private readonly innerPicker = viewChild(DatePicker);

    /**
     * Reports unparseable / out-of-range input to the bound form control.
     *
     * Without this the picker writes `undefined` to the model and renders its inline message, but the control
     * itself stays valid: the surrounding form submits and the entry the user typed is dropped without a word
     * (e.g. a competency saves with no soft due date).
     *
     * Only the parse/range failure is reported. Emptiness stays the consumer's business - a picker that must be
     * filled carries `Validators.required` on its control - and the (yellow) {@link warning} state is advisory.
     */
    validate(): ValidationErrors | null {
        return this.isInputValid() ? null : { invalidDate: true };
    }

    registerOnValidatorChange(fn: () => void) {
        this.onValidatorChange = fn;
    }

    /**
     * Sets the parse validity and tells the forms API to re-run {@link validate}.
     *
     * Most flips are followed by an `onChange` call, which revalidates on its own, but the programmatic paths
     * ({@link writeValue} / {@link updateSignals}) do not touch the model, so without this the control would
     * keep the stale error after a form reset.
     */
    private setInputValid(valid: boolean) {
        if (this.isInputValid() !== valid) {
            this.isInputValid.set(valid);
            this.onValidatorChange?.();
        }
    }

    constructor() {
        // Recheck the held value whenever a bound binds or moves. The bounds routinely arrive or change after a
        // value is already in the field: an exercise form feeds the due-date picker's [min] from the release date
        // the user is still editing. validate() reports the cached isInputValid, and neither updateField nor
        // updateSignals runs when only a bound changes, so without this the control would go on reporting a date
        // the range no longer allows as valid.
        effect(() => {
            const min = this.min();
            const max = this.max();
            // The recheck reads `value()` and writes `isInputValid`; keep those out of this effect's dependencies,
            // which are exactly the two bounds.
            untracked(() => this.revalidateAgainstBounds(min, max));
        });
    }

    /**
     * Recomputes range validity against the given bounds.
     *
     * Two states can be on screen when a bound moves, and they are not the same:
     * - a date the range rejected ({@link rejectedEntry}). It is a perfectly good date, so a bound that moves to
     *   include it makes the entry acceptable and it is committed, exactly as if the user had typed it now.
     * - unparseable text. `value()` does not represent what the field shows, so nothing can be recomputed from it
     *   and the error stands until the next edit.
     */
    private revalidateAgainstBounds(min?: dayjs.Dayjs, max?: dayjs.Dayjs) {
        const rejected = this.rejectedEntry;
        if (rejected) {
            if (this.isWithinRange(rejected, min, max)) {
                this.acceptRejectedEntry(rejected);
            }
            return;
        }
        const current = this.value();
        if (this.needsParentSync || current == undefined) {
            return;
        }
        const parsed = dayjs(current);
        // An unparseable held value is already flagged; moving a bound does not change that.
        if (parsed.isValid()) {
            this.setInputValid(this.isWithinRange(parsed, min, max));
        }
    }

    /**
     * Commits an entry the range had rejected, now that a bound moved to include it.
     *
     * The parent model still holds the `undefined` written when the entry was rejected, so it has to be handed the
     * date as well: leaving the control valid while the model is empty is the very hole this validator closes.
     */
    private acceptRejectedEntry(entry: dayjs.Dayjs) {
        this.rejectedEntry = undefined;
        this.needsParentSync = false;
        this.value.set(entry.toDate());
        this.dateInputValue.set(entry.toISOString());
        this.setInputValid(true);
        this.onChange?.(entry);
        this.valueChanged();
    }

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
    writeValue(value?: dayjs.Dayjs | Date | null) {
        // convert dayjs to date, because p-datepicker only works correctly with date objects
        const next = dayjs.isDayjs(value) ? value.toDate() : (value ?? null);
        // A programmatic write supersedes whatever the user had typed, so both pending-entry flags drop here,
        // ABOVE the idempotency guard. Resetting an already-empty field writes an equal value and takes the
        // early return below, and an entry left behind there would be written into the form by the next bound
        // change - handing the parent a date it had just discarded.
        // Either flag also means the input is showing raw text (kept by `keepInvalid`) that the model does not
        // hold, so the display has to be re-synced below or the field goes on showing a date the form does not
        // have - valid, and submitting nothing.
        const displayHoldsSupersededText = this.needsParentSync || this.rejectedEntry != undefined;
        this.rejectedEntry = undefined;
        this.needsParentSync = false;
        // Idempotency guard: Angular re-invokes writeValue on every change-detection pass while
        // p-datepicker's CVA write calls markForCheck. Re-setting the `value` signal with an equal
        // value would never let change detection settle (NG0103), so skip no-op writes.
        if (this.valuesEqual(this.value(), next)) {
            // The bound value is unchanged, but a prior unparseable entry may have left the validity
            // signals stale; refresh them so a programmatic reset/write clears any lingering invalid state.
            this.updateSignals();
            if (displayHoldsSupersededText) {
                this.reflectValueInPicker(next);
            }
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
     * re-checked after `writeValue`, so an edit form opens with the picker blank (e.g. the tutorial
     * free-period form). This only runs on the programmatic (form patch / reset) path; user
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
    registerOnTouched(_fn: () => void) {}

    /**
     *
     * @param fn
     */
    registerOnChange(fn: (val?: dayjs.Dayjs) => void) {
        this.onChange = fn;
    }

    /**
     * Whether a parsed value lies inside `[min]`/`[max]`. Both bounds are inclusive: a date equal to a
     * bound is accepted, which matches the calendar popup, where the bound days stay selectable.
     *
     * Shared by the typing path ({@link updateField}) and the programmatic one ({@link updateSignals}), so a
     * date the user is not allowed to type cannot slip in through `setValue` / `patchValue` either. The bounds
     * are parameters so the bounds effect in the constructor can pass the exact values it depends on.
     */
    private isWithinRange(parsed: dayjs.Dayjs, min = this.min(), max = this.max()): boolean {
        return !(min && parsed.isBefore(min)) && !(max && parsed.isAfter(max));
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

            // P2 fix: PrimeNG emits a real Date even for dates outside [minDate]/[maxDate]
            // because the calendar popup's disabled-day range only prevents picking — typed input
            // bypasses it. Reject out-of-range values here so the parent model never receives them
            // and the field shows as invalid. We do NOT clear this.value() so the displayed date
            // stays visible (keepInvalid-like); needsParentSync ensures recovery is propagated.
            if (!this.isWithinRange(parsed)) {
                this.setInputValid(false);
                this.dateInputValue.set(newValue.toISOString());
                this.needsParentSync = true;
                // Remember the date itself: it is valid in every respect except the current range, so a bound
                // that later moves to include it can accept the entry instead of making the user retype it.
                this.rejectedEntry = parsed;
                this.onChange?.(undefined);
                this.valueChanged();
                return;
            }

            // Always refresh validity (this also recovers from a previous unparseable entry).
            this.setInputValid(true);
            this.dateInputValue.set(newValue.toISOString());
            this.rejectedEntry = undefined;

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
            this.setInputValid(true);
            this.dateInputValue.set('');
            this.needsParentSync = false;
            this.rejectedEntry = undefined;
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
            this.setInputValid(false);
            this.dateInputValue.set(String(newValue));
            this.needsParentSync = true;
            // The typed text replaced whatever the range had rejected, so there is no entry left to accept.
            this.rejectedEntry = undefined;
            this.onChange?.(undefined);
        }
        this.valueChanged();
    }

    /** Records whether the current paste was triggered by a keyboard shortcut (Ctrl/Cmd+V or Shift+Insert). */
    onPickerKeydown(event: KeyboardEvent) {
        this.isKeyboardPaste = (event.key.toLowerCase() === 'v' && (event.ctrlKey || event.metaKey)) || (event.key === 'Insert' && event.shiftKey);
    }

    /**
     * Context-menu paste has no preceding keydown, so PrimeNG's `isKeydown` guard in `onUserInput`
     * is never set and the pasted text is silently ignored. We fix two things here:
     *
     * 1. If no text is currently selected in the input (cursor is just positioned), select all first
     *    so the browser's native paste replaces the entire field value instead of inserting at the
     *    cursor. We only do this for context-menu paste — Ctrl/Cmd+V is tracked separately via
     *    onPickerKeydown so we leave the user's cursor/selection intact in that case.
     *    We intentionally avoid relying on picker.isKeydown here: PrimeNG sets that flag for ANY
     *    keydown (including Arrow/Home/End), leaving it stale-true until the next input event.
     *
     * 2. Set isKeydown = true so PrimeNG's onUserInput actually processes the pasted text.
     */
    onPickerPaste(event: ClipboardEvent) {
        const picker = this.innerPicker();
        if (picker) {
            if (!this.isKeyboardPaste) {
                const input = event.target as HTMLInputElement;
                if (input.tagName === 'INPUT' && input.selectionStart === input.selectionEnd) {
                    input.select();
                }
            }
            this.isKeyboardPaste = false;
            picker.isKeydown = true;
        }
    }

    /**
     * Recomputes the validity signals from the currently bound value. Called after `writeValue`,
     * and exposed publicly so parents can refresh validation after programmatically patching the
     * bound form value (e.g. `exam-update` does this after a CD cycle).
     */
    updateSignals() {
        const currentValue = this.value();
        const parsed = currentValue != undefined ? dayjs(currentValue) : undefined;
        // An empty field is valid (the required check is handled separately); a present-but-unparseable one is not.
        // The range is checked here too: `setValue` / `patchValue` reach the model without passing updateField,
        // so without this a parent could write a date the user is not allowed to type and keep the form valid.
        this.setInputValid(parsed == undefined || (parsed.isValid() && this.isWithinRange(parsed)));
        this.dateInputValue.set(parsed?.isValid() ? parsed.toISOString() : '');
    }

    /**
     * p-datepicker accepts a valid *prefix* and silently ignores trailing characters, so
     * "13.06.2026 18:30adasdasdsad" parses to 13.06.2026 18:30 and looks valid.
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
            this.setInputValid(false);
            this.dateInputValue.set(raw);
            this.needsParentSync = true;
            // The text is not a full date, so there is nothing a moving bound could accept.
            this.rejectedEntry = undefined;
            this.onChange?.(undefined);
            this.valueChanged();
        }
    }

    /**
     * Confirm button for the time picker. A time-only picker shows the default time (startAt / current
     * time) in its spinner but does not write it to the model until the user nudges a spinner field, so
     * applying the shown time previously took two clicks (nudge + close). When the field is still empty,
     * commit the displayed time here so a single click applies it; otherwise just close.
     */
    applyAndClose(picker: DatePicker) {
        if (this.timeOnly() && this.value() == undefined) {
            this.updateField(this.startDate());
        }
        picker.hideOverlay();
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
