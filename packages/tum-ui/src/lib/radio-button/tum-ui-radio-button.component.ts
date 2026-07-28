import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Payload emitted by {@link TumUiRadioButtonComponent.onClick}. Mirrors PrimeNG's `RadioButtonClickEvent`
 * ({@code { originalEvent, value }}) so an admin `(onClick)="handler()"` binding keeps working when a
 * `p-radiobutton` is swapped for a `tum-ui-radio-button`.
 */
export interface TumUiRadioButtonClickEvent {
    originalEvent: MouseEvent;
    value: unknown;
}

const UNSET = Symbol('tum-ui-radio-unset');

/**
 * Radio button.
 *
 * Drop-in replacement for PrimeNG's `p-radiobutton`, built on a real, visually hidden native
 * `<input type="radio">` overlaying a token-styled circle — keeping keyboard support, `<label for>`
 * association, and screen-reader semantics. No PrimeNG / Bootstrap / CDK dependency.
 *
 * A radio renders "checked" when its own {@link value} equals the currently selected group value. That
 * selected value can arrive two ways, covering every admin usage:
 * - via `[ngModel]` / `[(ngModel)]` / `formControlName` (ControlValueAccessor) — this is the admin
 *   "one-way `[ngModel]="X ? X : undefined"` + `(onClick)`" pattern (the group value is written in, the
 *   `(onClick)` handler updates the source of truth, which flows back), and also classic two-way grouping.
 * - via the explicit {@link modelValue} input, for non-forms usage. A form binding, once present, wins.
 *
 * `(onClick)` fires on every click (including re-clicking the selected radio), matching PrimeNG and the
 * admin toggle handlers.
 */
@Component({
    selector: 'tum-ui-radio-button',
    templateUrl: './tum-ui-radio-button.component.html',
    styleUrl: './tum-ui-radio-button.component.scss',
    host: { class: 'tum-ui-radio-button' },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiRadioButtonComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiRadioButtonComponent implements ControlValueAccessor {
    /** The value this radio represents; it is checked when it equals the selected group value. */
    readonly value = input<unknown>();
    /** Explicit selected group value (non-forms usage). A form binding via CVA takes precedence when present. */
    readonly modelValue = input<unknown>();
    /** Radio group name, forwarded to the native input. */
    readonly name = input<string>();
    /** Forwarded to the native input's `id`, so an external `<label for=…>` associates correctly. */
    readonly inputId = input<string>();
    readonly disabled = input(false);
    /** Accessible label for the (visually hidden) input when no `<label for>` is present. */
    readonly ariaLabel = input<string>();

    /** Fires on every click. Named `onClick` for a drop-in swap of `p-radiobutton`'s `(onClick)`. */
    readonly onClick = output<TumUiRadioButtonClickEvent>();

    private readonly cvaValue = signal<unknown>(UNSET);
    private readonly selectedValue = computed(() => (this.cvaValue() === UNSET ? this.modelValue() : this.cvaValue()));
    protected readonly isChecked = computed(() => this.selectedValue() === this.value());

    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected readonly boxClasses = computed(() => {
        if (this.isDisabled()) {
            return 'bg-tum-ui-surface-200 border-tum-ui-surface-300 dark:bg-tum-ui-surface-700 dark:border-tum-ui-surface-600';
        }
        if (this.isChecked()) {
            return 'bg-tum-ui-primary border-tum-ui-primary';
        }
        return 'bg-tum-ui-surface-0 border-tum-ui-surface-300 dark:bg-tum-ui-surface-950 dark:border-tum-ui-surface-600';
    });

    protected readonly iconClasses = computed(() => (this.isDisabled() ? 'bg-tum-ui-surface-500 dark:bg-tum-ui-surface-400' : 'bg-tum-ui-surface-0'));

    private onModelChange: (value: unknown) => void = () => {};
    private onModelTouched: () => void = () => {};

    protected onInputClick(event: MouseEvent): void {
        if (this.isDisabled()) {
            return;
        }
        this.cvaValue.set(this.value());
        this.onModelChange(this.value());
        this.onModelTouched();
        this.onClick.emit({ originalEvent: event, value: this.value() });
    }

    protected onBlur(): void {
        this.onModelTouched();
    }

    writeValue(value: unknown): void {
        this.cvaValue.set(value);
    }

    registerOnChange(fn: (value: unknown) => void): void {
        this.onModelChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onModelTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.cvaDisabled.set(isDisabled);
    }
}
