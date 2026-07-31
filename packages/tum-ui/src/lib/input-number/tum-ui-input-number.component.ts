import { ChangeDetectionStrategy, Component, ElementRef, computed, forwardRef, input, linkedSignal, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp } from '@fortawesome/free-solid-svg-icons';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';

let nextInputNumberId = 0;

/** Integer input with locale grouping, optional affixes and step controls. */
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
    /** Lower bound applied on blur and stepping. */
    readonly min = input<number>();
    /** Upper bound applied on blur and stepping. */
    readonly max = input<number>();
    /** Increment / decrement applied by the stepper buttons and Arrow Up / Down keys. */
    readonly step = input<number>(1);
    /** Shows increment and decrement controls. */
    readonly showButtons = input(false);
    /** Text displayed before the formatted number. */
    readonly prefix = input<string>();
    /** Text displayed after the formatted number. */
    readonly suffix = input<string>();
    readonly placeholder = input<string>();
    readonly disabled = input(false);
    /** Marks the field invalid without changing its value. */
    readonly invalid = input(false);
    /** Expands the field to the available width. */
    readonly fluid = input(false);
    /** Enables locale-specific digit grouping. */
    readonly useGrouping = input(true);
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
    /** Classes appended to the component host. */
    readonly styleClass = input('');
    /** Classes appended to the native input. */
    readonly inputStyleClass = input('');

    private readonly inputRef = viewChild.required<ElementRef<HTMLInputElement>>('inputEl');
    private readonly cvaValue = signal<number | undefined>(undefined);
    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

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
    protected readonly inputClasses = computed(() => `tum-ui-input-number-input ${this.inputStyleClass()}`.trim());

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
        const formatted = new Intl.NumberFormat(this.locale(), { useGrouping: this.useGrouping(), maximumFractionDigits: 0 }).format(value);
        return `${this.prefix() ?? ''}${formatted}${this.suffix() ?? ''}`;
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

    private caretAfterDigits(text: string, digitCount: number): number {
        if (digitCount <= 0) {
            return (this.prefix() ?? '').length;
        }
        let seen = 0;
        for (let i = 0; i < text.length; i++) {
            if (text[i] >= '0' && text[i] <= '9') {
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
        if (parsed === undefined && el.value.includes('-') && !/\d/.test(el.value)) {
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
