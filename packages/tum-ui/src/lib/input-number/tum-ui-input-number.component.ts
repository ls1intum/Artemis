import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, computed, forwardRef, input, linkedSignal, numberAttribute, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';

let nextInputNumberId = 0;

interface LocaleNumberSyntax {
    digitBySymbol: ReadonlyMap<string, string>;
    digitSymbols: readonly string[];
    groupSeparators: readonly string[];
    minusSigns: readonly string[];
}

/** Integer input with locale grouping, optional affixes and step controls. */
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
    /** Locale used for formatting; omit it to use the browser locale. */
    readonly locale = input<string>();
    /** `id` of the inner `<input>`, so an external `<label for>` associates. Defaults to a unique per-instance id. */
    readonly inputId = input<string>(`tum-ui-input-number-${nextInputNumberId++}`);
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

    private readonly numberFormatter = computed(() => new Intl.NumberFormat(this.locale(), { useGrouping: this.useGrouping(), maximumFractionDigits: 0 }));
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
        return { digitBySymbol, digitSymbols, groupSeparators, minusSigns };
    }

    private parse(text: string): number | undefined {
        let body = text;
        const prefix = this.prefix();
        const suffix = this.suffix();
        if (prefix && body.startsWith(prefix)) {
            body = body.slice(prefix.length);
        }
        if (suffix && body.endsWith(suffix)) {
            body = body.slice(0, body.length - suffix.length);
        }
        const syntax = this.localeNumberSyntax();
        let digits = '';
        let negative = false;
        for (let index = 0; index < body.length;) {
            const digit = this.matchAt(body, index, syntax.digitSymbols);
            if (digit) {
                digits += syntax.digitBySymbol.get(digit)!;
                index += digit.length;
                continue;
            }
            const minusSign = this.matchAt(body, index, syntax.minusSigns);
            if (minusSign) {
                negative ||= digits.length === 0;
                index += minusSign.length;
                continue;
            }
            const groupSeparator = this.matchAt(body, index, syntax.groupSeparators);
            index += groupSeparator?.length ?? (body.codePointAt(index)! > 0xffff ? 2 : 1);
        }
        const normalized = `${negative ? '-' : ''}${digits}`;
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

    private matchAt(text: string, index: number, candidates: readonly string[]): string | undefined {
        return candidates.find((candidate) => candidate.length > 0 && text.startsWith(candidate, index));
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
        const next = this.clamp(base + delta);
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
