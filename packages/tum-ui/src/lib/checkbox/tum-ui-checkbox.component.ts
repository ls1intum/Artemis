import { ChangeDetectionStrategy, Component, booleanAttribute, computed, forwardRef, input, model, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faMinus } from '@fortawesome/free-solid-svg-icons';

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

    readonly changed = output<TumUiCheckboxChangeEvent>();

    protected readonly faCheck = faCheck;
    protected readonly faMinus = faMinus;

    // Indeterminate takes visual precedence over checked, as a native `<input indeterminate>` does: the dash
    // shows whenever indeterminate is set, the tick only when checked and NOT indeterminate.
    protected readonly showDash = computed(() => this.indeterminate());
    protected readonly showTick = computed(() => this.checked() && !this.indeterminate());

    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected readonly boxClasses = computed(() => {
        if (this.isDisabled()) {
            return 'tum:bg-disabled-background tum:border-control-border';
        }
        // Both the checked tick and the indeterminate dash sit on a brand-filled box.
        if (this.checked() || this.indeterminate()) {
            return 'tum:bg-primary tum:border-primary';
        }
        return 'tum:bg-control-background tum:border-control-border';
    });

    protected readonly iconClasses = computed(() => (this.isDisabled() ? 'tum:text-disabled' : 'tum:text-primary-contrast'));

    private onModelChange: (value: boolean) => void = () => {};
    private onModelTouched: () => void = () => {};

    protected onInputChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        const newChecked = input.checked;
        this.checked.set(newChecked);
        this.onModelChange(newChecked);
        this.onModelTouched();
        this.changed.emit({ originalEvent: event, checked: newChecked });
        input.checked = this.checked();
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
