import { ChangeDetectionStrategy, Component, booleanAttribute, computed, forwardRef, input, model, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faMinus } from '@fortawesome/free-solid-svg-icons';

export interface TumAetUiCheckboxChangeEvent {
    originalEvent: Event;
    checked: boolean;
}

@Component({
    selector: 'tumaet-ui-checkbox',
    templateUrl: './tumaet-ui-checkbox.component.html',
    styleUrl: './tumaet-ui-checkbox.component.scss',
    imports: [FaIconComponent],
    host: { class: 'tumaet-ui-checkbox' },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumAetUiCheckboxComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiCheckboxComponent implements ControlValueAccessor {
    readonly disabled = input(false, { transform: booleanAttribute });

    readonly inputId = input<string>();

    readonly name = input<string>();

    readonly ariaLabel = input<string>();

    readonly checked = model(false);

    /**
     * Renders the partial-selection dash instead of the tick, for a select-all control whose rows are only
     * partly selected. Purely visual: it never changes `checked`, the model, or what `changed` emits.
     */
    readonly indeterminate = input(false, { transform: booleanAttribute });

    readonly changed = output<TumAetUiCheckboxChangeEvent>();

    protected readonly faCheck = faCheck;
    protected readonly faMinus = faMinus;

    protected readonly showDash = computed(() => this.indeterminate());
    protected readonly showTick = computed(() => this.checked() && !this.indeterminate());

    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected readonly boxClasses = computed(() => {
        if (this.isDisabled()) {
            return 'tumaet:bg-disabled-background tumaet:border-control-border';
        }
        if (this.checked() || this.indeterminate()) {
            return 'tumaet:bg-primary tumaet:border-primary';
        }
        return 'tumaet:bg-control-background tumaet:border-control-border';
    });

    protected readonly iconClasses = computed(() => (this.isDisabled() ? 'tumaet:text-disabled' : 'tumaet:text-primary-contrast'));

    private onModelChange: (value: boolean) => void = () => {};
    private onModelTouched: () => void = () => {};

    protected onInputChange(event: Event): void {
        const newChecked = (event.target as HTMLInputElement).checked;
        this.checked.set(newChecked);
        this.onModelChange(newChecked);
        this.onModelTouched();
        this.changed.emit({ originalEvent: event, checked: newChecked });
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
