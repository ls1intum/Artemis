import { ChangeDetectionStrategy, Component, computed, forwardRef, input, model, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck } from '@fortawesome/free-solid-svg-icons';

export interface TumUiCheckboxChangeEvent {
    originalEvent: Event;
    checked: boolean;
}

@Component({
    selector: 'tum-ui-checkbox',
    templateUrl: './tum-ui-checkbox.component.html',
    styleUrl: './tum-ui-checkbox.component.scss',
    imports: [FaIconComponent],
    host: { class: 'tum-ui-checkbox' },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiCheckboxComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCheckboxComponent implements ControlValueAccessor {
    readonly disabled = input(false);

    readonly inputId = input<string>();

    readonly name = input<string>();

    readonly ariaLabel = input<string>();

    readonly checked = model(false);

    readonly onChange = output<TumUiCheckboxChangeEvent>();

    protected readonly faCheck = faCheck;

    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected readonly boxClasses = computed(() => {
        if (this.isDisabled()) {
            return 'bg-tum-ui-surface-200 border-tum-ui-surface-300 dark:bg-tum-ui-surface-700 dark:border-tum-ui-surface-600';
        }
        if (this.checked()) {
            return 'bg-tum-ui-primary border-tum-ui-primary';
        }
        return 'bg-tum-ui-surface-0 border-tum-ui-surface-300 dark:bg-tum-ui-surface-950 dark:border-tum-ui-surface-600';
    });

    protected readonly iconClasses = computed(() => (this.isDisabled() ? 'text-tum-ui-surface-500 dark:text-tum-ui-surface-400' : 'text-tum-ui-surface-0'));

    private onModelChange: (value: boolean) => void = () => {};
    private onModelTouched: () => void = () => {};

    protected onInputChange(event: Event): void {
        const newChecked = (event.target as HTMLInputElement).checked;
        this.checked.set(newChecked);
        this.onModelChange(newChecked);
        this.onModelTouched();
        this.onChange.emit({ originalEvent: event, checked: newChecked });
    }

    protected onBlur(): void {
        this.onModelTouched();
    }

    writeValue(value: boolean): void {
        this.checked.set(!!value);
    }

    registerOnChange(fn: (value: boolean) => void): void {
        this.onModelChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onModelTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.cvaDisabled.set(isDisabled);
    }
}
