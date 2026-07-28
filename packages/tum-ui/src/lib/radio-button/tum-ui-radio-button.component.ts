import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface TumUiRadioButtonClickEvent {
    originalEvent: MouseEvent;
    value: unknown;
}

const UNSET = Symbol('tum-ui-radio-unset');

@Component({
    selector: 'tum-ui-radio-button',
    templateUrl: './tum-ui-radio-button.component.html',
    styleUrl: './tum-ui-radio-button.component.scss',
    host: { class: 'tum-ui-radio-button' },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiRadioButtonComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiRadioButtonComponent implements ControlValueAccessor {
    readonly value = input<unknown>();

    readonly modelValue = input<unknown>();

    readonly name = input<string>();

    readonly inputId = input<string>();
    readonly disabled = input(false);

    readonly ariaLabel = input<string>();

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
