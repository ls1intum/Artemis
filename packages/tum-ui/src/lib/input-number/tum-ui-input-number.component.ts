import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, computed, forwardRef, input, linkedSignal, numberAttribute, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';

interface LocaleNumberSyntax {
    digitBySymbol: ReadonlyMap<string, string>;
    digitSymbols: readonly string[];
    groupSeparators: readonly string[];
    decimalSeparators: readonly string[];
    minusSigns: readonly string[];
}

/** Numeric input with locale grouping, optional affixes, optional decimals and step controls. */
@Component({
    selector: 'tum-ui-input-number',
    templateUrl: './tum-ui-input-number.component.html',
    styleUrl: './tum-ui-input-number.component.scss',
    imports: [TumUiInputDirective, FaIconComponent],
    host: {
        class: 'tum-ui-input-number',
        '[class.tum-ui-input-number-fluid]': 'fluid()',
        '[class.tum-ui-input-number-buttons]': 'showButtons()',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiInputNumberComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiInputNumberComponent implements ControlValueAccessor {
    /** Lower bound applied on blur and stepping. */
    readonly min = input<number>();
    /** Upper bound applied on blur and stepping. */
    readonly max = input<number>();
    /** Increment / decrement applied by the stepper buttons and Arrow Up / Down keys. */
    readonly step = input(1, { transform: numberAttribute });
    /** Shows increment and decrement controls. */
    readonly showButtons = input(false, { transform: booleanAttribute });
    /** Text displayed before the formatted number. */
    readonly prefix = input<string>();
    /** Text displayed after the formatted number. */
    readonly suffix = input<string>();
    readonly placeholder = input<string>();
    readonly disabled = input(false, { transform: booleanAttribute });
    /** Marks the field invalid without changing its value. */
    readonly invalid = input(false, { transform: booleanAttribute });
    /** Expands the field to the available width. */
    readonly fluid = input(false, { transform: booleanAttribute });
    /** Enables locale-specific digit grouping. */
    readonly useGrouping = input(true, { transform: booleanAttribute });
    /**
     * Maximum fraction digits. `0` (the default) keeps the field integer-only; a positive value lets the user
     * type the locale's decimal separator, and the fraction is truncated — not rounded — to this many digits.
     */
    readonly maxFractionDigits = input(0, { transform: numberAttribute });
    /** Locale used for formatting; omit it to use the browser locale. */
    readonly locale = input<string>();
    /**
     * `id` of the inner `<input>`, so an external `<label for>` associates. Defaults to the id of an enclosing
     * `tum-ui-form-field`, and to a unique per-instance id outside one.
     */
    readonly inputId = input<string>();
    /** Native input name. */
    readonly name = input<string>();
    /** Accessible name for the inner `<input>` when there is no visible `<label>`. */
    readonly ariaLabel = input<string>();
    /** Element `id` values that label the inner `<input>`. */
    readonly ariaLabelledBy = input<string>();
    /** Element `id` values that describe the inner `<input>`. */
    readonly ariaDescribedBy = input<string>();
    private readonly inputRef = viewChild.required<ElementRef<HTMLInputElement>>('inputEl');
    private readonly cvaValue = signal<number | undefined>(undefined);
    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;

    private readonly numberFormatter = computed(() => new Intl.NumberFormat(this.locale(), { useGrouping: this.useGrouping(), maximumFractionDigits: this.maxFractionDigits() }));
    private readonly localeNumberSyntax = computed(() => this.createLocaleNumberSyntax());
    private readonly formattedValue = computed(() => this.format(this.cvaValue()));
    protected readonly displayText = linkedSignal(() => this.formattedValue());
    protected readonly ariaValueNow = computed(() => this.cvaValue());
    protected readonly ariaValueText = computed(() => this.formattedValue() || null);

    private onModelChange: (value: number | undefined) => void = () => {};
    private onModelTouched: () => void = () => {};

    private format(value: number | undefined): string {
        if (value === undefined || value === null || Number.isNaN(value)) {
            return '';
        }
        const formatted = this.numberFormatter().format(value);
        return `${this.prefix() ?? ''}${formatted}${this.suffix() ?? ''}`;
    }

    private createLocaleNumberSyntax(): LocaleNumberSyntax {
        const formatter = new Intl.NumberFormat(this.locale(), { useGrouping: true, maximumFractionDigits: 0 });
        const digitBySymbol = new Map<string, string>();
        for (let digit = 0; digit <= 9; digit++) {
            const localizedDigit = formatter
                .formatToParts(digit)
                .filter((part) => part.type === 'integer')
                .map((part) => part.value)
                .join('');
            const asciiDigit = String(digit);
            digitBySymbol.set(asciiDigit, asciiDigit);
            digitBySymbol.set(localizedDigit, asciiDigit);
        }
        const digitSymbols = [...digitBySymbol.keys()].sort((left, right) => right.length - left.length);
        const groupSeparators = [
            ...new Set(
                formatter
                    .formatToParts(123456789)
                    .filter((part) => part.type === 'group')
                    .map((part) => part.value),
            ),
        ].sort((left, right) => right.length - left.length);
        const minusSigns = [
            ...new Set([
                '-',
                ...formatter
                    .formatToParts(-1)
                    .filter((part) => part.type === 'minusSign')
                    .map((part) => part.value),
            ]),
        ].sort((left, right) => right.length - left.length);
        // Only the locale's own decimal symbol counts, never a hardcoded `.`: German uses `.` to group
        // thousands, so accepting it would read `1.234` as a fraction instead of a grouped integer.
        const decimalSeparators = [
            ...new Set(
                new Intl.NumberFormat(this.locale(), { minimumFractionDigits: 1 })
                    .formatToParts(1.1)
                    .filter((part) => part.type === 'decimal')
                    .map((part) => part.value),
            ),
        ].sort((left, right) => right.length - left.length);
        return { digitBySymbol, digitSymbols, groupSeparators, decimalSeparators, minusSigns };
    }

    private stripAffixes(text: string): string {
        let body = text;
        const prefix = this.prefix();
        const suffix = this.suffix();
        if (prefix && body.startsWith(prefix)) {
            body = body.slice(prefix.length);
        }
        if (suffix && body.endsWith(suffix)) {
            body = body.slice(0, body.length - suffix.length);
        }
        return body;
    }

    private parse(text: string): number | undefined {
        const body = this.stripAffixes(text);
        const syntax = this.localeNumberSyntax();
        const maxFractionDigits = this.maxFractionDigits();
        let integerDigits = '';
        let fractionDigits = '';
        let negative = false;
        let inFraction = false;
        for (let index = 0; index < body.length;) {
            const digit = this.matchAt(body, index, syntax.digitSymbols);
            if (digit) {
                if (inFraction) {
                    // Truncate rather than round: the user is still typing, and rounding here would fight the caret.
                    if (fractionDigits.length < maxFractionDigits) {
                        fractionDigits += syntax.digitBySymbol.get(digit)!;
                    }
                } else {
                    integerDigits += syntax.digitBySymbol.get(digit)!;
                }
                index += digit.length;
                continue;
            }
            const minusSign = this.matchAt(body, index, syntax.minusSigns);
            if (minusSign) {
                negative ||= integerDigits.length === 0;
                index += minusSign.length;
                continue;
            }
            if (maxFractionDigits > 0 && !inFraction) {
                const decimalSeparator = this.matchAt(body, index, syntax.decimalSeparators);
                if (decimalSeparator) {
                    inFraction = true;
                    index += decimalSeparator.length;
                    continue;
                }
            }
            const groupSeparator = this.matchAt(body, index, syntax.groupSeparators);
            index += groupSeparator?.length ?? (body.codePointAt(index)! > 0xffff ? 2 : 1);
        }
        if (integerDigits === '' && fractionDigits === '') {
            return undefined;
        }
        // Always assembled with an ASCII `.`, which is what Number.parseFloat understands regardless of locale.
        const parsed = Number.parseFloat(`${negative ? '-' : ''}${integerDigits || '0'}.${fractionDigits || '0'}`);
        return Number.isNaN(parsed) ? undefined : parsed;
    }

    /**
     * True while the text holds a fraction the user is still entering that reformatting would swallow — a
     * trailing decimal separator (`12.`) or trailing fraction zeros (`12.50`). The raw text is kept until the
     * entry settles on blur, mirroring the lone-minus-sign guard in {@link onInput}.
     */
    private fractionEntryInProgress(text: string): boolean {
        if (this.maxFractionDigits() === 0) {
            return false;
        }
        const body = this.stripAffixes(text);
        const syntax = this.localeNumberSyntax();
        for (let index = 0; index < body.length;) {
            const decimalSeparator = this.matchAt(body, index, syntax.decimalSeparators);
            if (decimalSeparator) {
                // Compared as ASCII so locales with their own digit symbols are handled like any other.
                const fraction = this.toAsciiDigits(body.slice(index + decimalSeparator.length));
                return fraction === '' || fraction.endsWith('0');
            }
            index += body.codePointAt(index)! > 0xffff ? 2 : 1;
        }
        return false;
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

    private matchAt(text: string, index: number, candidates: readonly string[]): string | undefined {
        return candidates.find((candidate) => candidate.length > 0 && text.startsWith(candidate, index));
    }

    private toAsciiDigits(text: string): string {
        const syntax = this.localeNumberSyntax();
        let digits = '';
        for (let index = 0; index < text.length;) {
            const digit = this.matchAt(text, index, syntax.digitSymbols);
            if (digit) {
                digits += syntax.digitBySymbol.get(digit)!;
                index += digit.length;
                continue;
            }
            index += text.codePointAt(index)! > 0xffff ? 2 : 1;
        }
        return digits;
    }

    private digitCount(text: string): number {
        const digitSymbols = this.localeNumberSyntax().digitSymbols;
        let count = 0;
        for (let index = 0; index < text.length;) {
            const digit = this.matchAt(text, index, digitSymbols);
            if (digit) {
                count++;
                index += digit.length;
                continue;
            }
            index += text.codePointAt(index)! > 0xffff ? 2 : 1;
        }
        return count;
    }

    private caretAfterDigits(text: string, digitCount: number): number {
        const start = (this.prefix() ?? '').length;
        if (digitCount <= 0) {
            return start;
        }
        const end = text.length - (this.suffix() ?? '').length;
        const digitSymbols = this.localeNumberSyntax().digitSymbols;
        let seen = 0;
        for (let index = start; index < end;) {
            const digit = this.matchAt(text, index, digitSymbols);
            if (digit) {
                seen++;
                if (seen === digitCount) {
                    return index + digit.length;
                }
            }
            index += digit?.length ?? (text.codePointAt(index)! > 0xffff ? 2 : 1);
        }
        return end;
    }

    protected onInput(event: Event): void {
        const el = event.target as HTMLInputElement;
        const caret = el.selectionStart ?? el.value.length;
        const prefixLength = (this.prefix() ?? '').length;
        const digitsBeforeCaret = this.digitCount(el.value.slice(prefixLength, caret));
        const parsed = this.parse(el.value);
        this.cvaValue.set(parsed);
        this.onModelChange(parsed);
        const syntax = this.localeNumberSyntax();
        if (parsed === undefined && syntax.minusSigns.some((minusSign) => el.value.includes(minusSign)) && this.digitCount(el.value) === 0) {
            this.displayText.set(el.value);
            return;
        }
        if (this.fractionEntryInProgress(el.value)) {
            this.displayText.set(el.value);
            return;
        }
        const formatted = this.format(parsed);
        this.displayText.set(formatted);
        el.value = formatted;
        const nextCaret = this.caretAfterDigits(formatted, digitsBeforeCaret);
        el.setSelectionRange(nextCaret, nextCaret);
    }

    protected onStep(delta: number): void {
        if (this.isDisabled()) {
            return;
        }
        const base = this.cvaValue() ?? 0;
        // Round to the field's precision so a fractional step does not leak binary float error into the model
        // (0.2 + 0.1 is 0.30000000000000004). Harmless in integer mode, where the step is a whole number.
        const stepped = Number((base + delta).toFixed(this.maxFractionDigits()));
        const next = this.clamp(stepped);
        this.cvaValue.set(next);
        this.displayText.set(this.format(next));
        this.onModelChange(next);
        this.inputRef().nativeElement.focus();
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

    protected onBlurHandler(): void {
        const value = this.cvaValue();
        if (value !== undefined) {
            const clamped = this.clamp(value);
            if (clamped !== value) {
                this.cvaValue.set(clamped);
                this.onModelChange(clamped);
            }
        }
        this.displayText.set(this.format(this.cvaValue()));
        this.onModelTouched();
    }

    writeValue(value: number | undefined): void {
        this.cvaValue.set(value ?? undefined);
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
