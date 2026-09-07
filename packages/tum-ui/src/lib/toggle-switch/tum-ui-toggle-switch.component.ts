import { ChangeDetectionStrategy, Component, HostAttributeToken, booleanAttribute, computed, forwardRef, inject, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
    selector: 'tum-ui-toggle-switch',
    templateUrl: './tum-ui-toggle-switch.component.html',
    styleUrl: './tum-ui-toggle-switch.component.scss',
    host: {
        // The identity class is static so it is always present; only the state classes are bound.
        class: 'tum-ui-toggle-switch',
        '[class]': 'hostClasses()',
        '[attr.data-checked]': 'checked()',
        '[attr.data-disabled]': 'effectiveDisabled() || null',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiToggleSwitchComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiToggleSwitchComponent implements ControlValueAccessor {
    private readonly hostAriaLabel = inject(new HostAttributeToken('aria-label'), { optional: true });
    private readonly hostAriaLabelledBy = inject(new HostAttributeToken('aria-labelledby'), { optional: true });

    readonly disabled = input(false, { transform: booleanAttribute });

    readonly inputId = input<string>();

    readonly ariaLabel = input<string>();

    readonly ariaLabelledBy = input<string>();

    readonly changed = output<boolean>();

    protected readonly checked = signal(false);
    private readonly cvaDisabled = signal(false);
    protected readonly effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled());
    protected readonly effectiveAriaLabel = computed(() => this.ariaLabel() ?? this.hostAriaLabel);
    protected readonly effectiveAriaLabelledBy = computed(() => this.ariaLabelledBy() ?? this.hostAriaLabelledBy);

    protected onChange: (value: boolean) => void = () => {};
    protected onTouched: () => void = () => {};

    protected readonly hostClasses = computed(() => {
        const track = this.checked() ? 'tum:bg-primary' : 'tum:bg-control-border';
        return `${track} ${this.effectiveDisabled() ? 'tum:opacity-60' : ''}`.trim();
    });

    protected onInputChange(event: Event): void {
        const next = (event.target as HTMLInputElement).checked;
        this.checked.set(next);
        this.onChange(next);
        this.onTouched();
        this.changed.emit(next);
    }

    protected onInputBlur(): void {
        this.onTouched();
    }

    writeValue(value: boolean): void {
        this.checked.set(!!value);
    }

    registerOnChange(fn: (value: boolean) => void): void {
        this.onChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.cvaDisabled.set(isDisabled);
    }
}
