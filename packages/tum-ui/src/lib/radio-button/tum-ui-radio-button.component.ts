import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface TumUiRadioButtonSelectEvent {
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

    readonly name = input<string>();

    readonly inputId = input<string>();
    readonly disabled = input(false);

    readonly ariaLabel = input<string>();

    readonly selected = output<TumUiRadioButtonSelectEvent>();

    private readonly cvaValue = signal<unknown>(UNSET);
    protected readonly isChecked = computed(() => this.cvaValue() !== UNSET && this.cvaValue() === this.value());

    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected readonly boxClasses = computed(() => {
        if (this.isDisabled()) {
            return 'tum:bg-disabled-background tum:border-control-border';
        }
        if (this.isChecked()) {
            return 'tum:bg-primary tum:border-primary';
        }
        return 'tum:bg-control-background tum:border-control-border';
    });

    protected readonly iconClasses = computed(() => (this.isDisabled() ? 'tum:bg-disabled' : 'tum:bg-primary-contrast'));

    private onModelChange: (value: unknown) => void = () => {};
    private onModelTouched: () => void = () => {};

    protected onInputClick(event: MouseEvent): void {
        if (this.isDisabled()) {
            return;
        }
        this.cvaValue.set(this.value());
        this.onModelChange(this.value());
        this.onModelTouched();
        this.selected.emit({ originalEvent: event, value: this.value() });
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
