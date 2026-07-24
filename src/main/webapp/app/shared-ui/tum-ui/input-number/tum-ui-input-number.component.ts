import { ChangeDetectionStrategy, Component, ElementRef, LOCALE_ID, computed, effect, forwardRef, inject, input, output, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { TumUiInputDirective } from 'app/shared-ui/tum-ui/input/tum-ui-input.directive';

// Per-instance id so a `<label for>` can target the inner input when no explicit `inputId` is given.
let nextInputNumberId = 0;

/**
 * Numeric input, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-inputnumber` for integer inputs: a token-styled text field that shows a
 * locale-formatted number (optional grouping, optional prefix / suffix) with an optional stacked stepper. The
 * value is bound as a plain `number | undefined` through a {@link ControlValueAccessor}, so `[(ngModel)]`,
 * `[ngModel]` + `(ngModelChange)`, and reactive `formControlName` all work. Empty input clears to `undefined`;
 * the value is clamped to `[min, max]` on blur and on every step, matching `p-inputnumber`.
 *
 * Scope: integer values (all Artemis usages are integers). Decimal / currency / fraction-digit modes are not
 * implemented; add them here if a consumer needs them.
 */
@Component({
    selector: 'tum-ui-input-number',
    templateUrl: './tum-ui-input-number.component.html',
    styleUrl: './tum-ui-input-number.component.scss',
    imports: [TumUiInputDirective, FaIconComponent],
    host: { '[class]': 'hostClasses()' },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiInputNumberComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiInputNumberComponent implements ControlValueAccessor {
    /** Lower bound; the value is clamped to it on blur / step (parity with `p-inputnumber [min]`). */
    readonly min = input<number>();
    /** Upper bound; the value is clamped to it on blur / step (parity with `[max]`). */
    readonly max = input<number>();
    /** Increment / decrement applied by the stepper buttons and Arrow Up / Down keys. */
    readonly step = input<number>(1);
    /** Render the stacked increment / decrement stepper on the trailing edge (parity with `[showButtons]`). */
    readonly showButtons = input(false);
    /** Text shown before the number, inside the field (parity with `[prefix]`, e.g. `"every "`). */
    readonly prefix = input<string>();
    /** Text shown after the number, inside the field (parity with `[suffix]`, e.g. `" week(s)"`). */
    readonly suffix = input<string>();
    readonly placeholder = input<string>();
    readonly disabled = input(false);
    /** Danger border for an external error state (parity with `[invalid]`); forwarded to the inner `tumUiInput`. */
    readonly invalid = input(false);
    /** Full-width field (parity with `[fluid]`). */
    readonly fluid = input(false);
    /** Locale digit grouping (parity with `[useGrouping]`); `true` shows e.g. `5,000`. */
    readonly useGrouping = input(true);
    /** `id` of the inner `<input>`, so an external `<label for>` associates. Defaults to a unique per-instance id. */
    readonly inputId = input<string>(`tum-ui-input-number-${nextInputNumberId++}`);
    /** `name` forwarded onto the inner `<input>` for template-driven-form parity. */
    readonly name = input<string>();
    /** Accessible name for the inner `<input>` when there is no visible `<label>`. */
    readonly ariaLabel = input<string>();
    /** Extra classes forwarded onto the host (drop-in for `styleClass`). */
    readonly styleClass = input('');
    /** Extra classes forwarded onto the inner `<input>` (drop-in for `inputStyleClass`). */
    readonly inputStyleClass = input('');

    /** Fires on the inner input's blur, after the value is clamped / reformatted (parity with `(onBlur)`). */
    readonly onBlur = output<FocusEvent>();
    /** Fires on the inner input's focus (parity with `(onFocus)`). */
    readonly onFocus = output<FocusEvent>();

    private readonly inputRef = viewChild<ElementRef<HTMLInputElement>>('inputEl');
    private readonly locale = inject(LOCALE_ID);

    // The model value delivered through the CVA (ngModel / formControl). `undefined` = empty.
    private readonly cvaValue = signal<number | undefined>(undefined);
    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());
    // While the field is focused the user is typing, so the displayed text is owned by onInput (which preserves the
    // caret); the sync effect must not overwrite it. Reformatting resumes on blur / external writes.
    private readonly focused = signal(false);

    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;

    protected readonly hostClasses = computed(() => {
        const parts = ['tum-ui-input-number'];
        if (this.fluid()) {
            parts.push('tum-ui-input-number-fluid');
        }
        if (this.showButtons()) {
            parts.push('tum-ui-input-number-buttons');
        }
        const style = this.styleClass();
        return style ? `${parts.join(' ')} ${style}` : parts.join(' ');
    });

    // WAI-ARIA spinbutton semantics: the input announces its numeric value + range and is adjusted with the arrow
    // keys (see onKeydown), so screen-reader / keyboard users get the stepper affordance without the mouse-only
    // increment / decrement buttons (which stay `aria-hidden`). `aria-valuetext` carries the human-readable
    // display (incl. any prefix / suffix), `aria-valuenow` the raw number.
    protected readonly ariaValueNow = computed(() => this.cvaValue());
    protected readonly ariaValueText = computed(() => this.format(this.cvaValue()) || null);

    private onModelChange: (value: number | undefined) => void = () => {};
    private onModelTouched: () => void = () => {};

    constructor() {
        // Keep the field's displayed text in sync with the model whenever the value or a formatting input changes —
        // but never while the user is typing (onInput owns the text then, to preserve the caret).
        effect(() => {
            const value = this.cvaValue();
            // establish reactive deps on the formatting inputs
            this.prefix();
            this.suffix();
            this.useGrouping();
            const el = this.inputRef()?.nativeElement;
            if (el && !this.focused()) {
                el.value = this.format(value);
            }
        });
    }

    private format(value: number | undefined): string {
        if (value === undefined || value === null || Number.isNaN(value)) {
            return '';
        }
        const formatted = new Intl.NumberFormat(this.locale, { useGrouping: this.useGrouping(), maximumFractionDigits: 0 }).format(value);
        return `${this.prefix() ?? ''}${formatted}${this.suffix() ?? ''}`;
    }

    /** Extract the integer from the field text, ignoring the prefix / suffix / grouping separators. */
    private parse(text: string): number | undefined {
        // Strip the configured affixes first so a digit inside `prefix`/`suffix` is never read as part of the value.
        let body = text;
        const prefix = this.prefix();
        const suffix = this.suffix();
        if (prefix && body.startsWith(prefix)) {
            body = body.slice(prefix.length);
        }
        if (suffix && body.endsWith(suffix)) {
            body = body.slice(0, body.length - suffix.length);
        }
        const digits = body.replace(/[^\d-]/g, '');
        const normalized = digits.startsWith('-') ? '-' + digits.slice(1).replace(/-/g, '') : digits.replace(/-/g, '');
        if (normalized === '' || normalized === '-') {
            return undefined;
        }
        const parsed = Number.parseInt(normalized, 10);
        return Number.isNaN(parsed) ? undefined : parsed;
    }

    private clamp(value: number): number {
        const min = this.min();
        const max = this.max();
        let clamped = value;
        if (min !== undefined && clamped < min) {
            clamped = min;
        }
        if (max !== undefined && clamped > max) {
            clamped = max;
        }
        return clamped;
    }

    /** The caret offset that sits just after the `digitCount`-th digit of `text` (keeps the caret stable while formatting). */
    private caretForDigits(text: string, digitCount: number): number {
        if (digitCount <= 0) {
            return (this.prefix() ?? '').length;
        }
        let seen = 0;
        for (let i = 0; i < text.length; i++) {
            if (/\d/.test(text[i])) {
                seen++;
                if (seen === digitCount) {
                    return i + 1;
                }
            }
        }
        return text.length - (this.suffix() ?? '').length;
    }

    protected onInput(event: Event): void {
        const el = event.target as HTMLInputElement;
        const caret = el.selectionStart ?? el.value.length;
        const digitsBeforeCaret = (el.value.slice(0, caret).match(/\d/g) ?? []).length;
        const parsed = this.parse(el.value);
        this.cvaValue.set(parsed);
        this.onModelChange(parsed);
        // Mid-typing a negative number ("-" before any digit): leave the raw text so the minus is not erased on
        // this keystroke; the model stays `undefined` until a digit arrives, then formatting resumes normally.
        if (parsed === undefined && el.value.includes('-') && !/\d/.test(el.value)) {
            return;
        }
        // Reformat live (grouping + prefix / suffix) and restore the caret after the same number of digits.
        const formatted = this.format(parsed);
        el.value = formatted;
        const nextCaret = this.caretForDigits(formatted, digitsBeforeCaret);
        el.setSelectionRange(nextCaret, nextCaret);
    }

    protected onStep(delta: number): void {
        if (this.isDisabled()) {
            return;
        }
        // Step from `0` (not `min`) when empty, then clamp — so a first increment from an empty field lands on
        // `min` rather than `min + step`, matching p-inputnumber (which spins from `value || 0`).
        const base = this.cvaValue() ?? 0;
        const next = this.clamp(base + delta);
        this.cvaValue.set(next);
        this.onModelChange(next);
        const el = this.inputRef()?.nativeElement;
        if (el) {
            el.value = this.format(next);
        }
    }

    protected onKeydown(event: KeyboardEvent): void {
        if (event.key === 'ArrowUp') {
            event.preventDefault();
            this.onStep(this.step());
        } else if (event.key === 'ArrowDown') {
            event.preventDefault();
            this.onStep(-this.step());
        }
    }

    protected onBlurHandler(event: FocusEvent): void {
        this.focused.set(false);
        const value = this.cvaValue();
        if (value !== undefined) {
            const clamped = this.clamp(value);
            if (clamped !== value) {
                this.cvaValue.set(clamped);
                this.onModelChange(clamped);
            }
        }
        const el = this.inputRef()?.nativeElement;
        if (el) {
            el.value = this.format(this.cvaValue());
        }
        this.onModelTouched();
        this.onBlur.emit(event);
    }

    protected onFocusHandler(event: FocusEvent): void {
        this.focused.set(true);
        this.onFocus.emit(event);
    }

    /** Focus the inner native input (public handle for consumers, e.g. a page-navigation field). */
    focus(): void {
        this.inputRef()?.nativeElement.focus();
    }

    /** Blur the inner native input. */
    blur(): void {
        this.inputRef()?.nativeElement.blur();
    }

    /** Select the inner native input's text. */
    select(): void {
        this.inputRef()?.nativeElement.select();
    }

    writeValue(value: number | undefined): void {
        this.cvaValue.set(value ?? undefined);
        const el = this.inputRef()?.nativeElement;
        if (el && !this.focused()) {
            el.value = this.format(this.cvaValue());
        }
    }

    registerOnChange(fn: (value: number | undefined) => void): void {
        this.onModelChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onModelTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.cvaDisabled.set(isDisabled);
    }
}
