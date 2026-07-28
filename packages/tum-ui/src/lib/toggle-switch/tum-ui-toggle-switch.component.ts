import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Boolean on/off switch.
 *
 * Drop-in replacement for PrimeNG's `p-toggleswitch`: same 2.5rem × 1.5rem pill track with a sliding
 * 1rem handle, reproduced from the Aura `toggleswitch` tokens + base style. `bg-tum-ui-primary` when on, the
 * surface ramp when off — dark-mode-correct for free because the tokens resolve per theme.
 *
 * The host element itself carries `role="switch"` (so a template-level `aria-label` / `data-testid`
 * lands on the right element) and is keyboard-operable (Space / Enter). It implements
 * `ControlValueAccessor`, so it works with `[(ngModel)]`, one-way `[ngModel]` + a change handler
 * (the pattern the admin feature-toggle and passkey screens use), and reactive `formControlName`.
 */
@Component({
    selector: 'tum-ui-toggle-switch',
    templateUrl: './tum-ui-toggle-switch.component.html',
    styleUrl: './tum-ui-toggle-switch.component.scss',
    host: {
        role: 'switch',
        '[class]': 'hostClasses()',
        '[attr.id]': 'inputId() || null',
        '[attr.aria-checked]': 'checked()',
        '[attr.aria-disabled]': 'effectiveDisabled() || null',
        '[attr.tabindex]': 'effectiveDisabled() ? -1 : 0',
        '[attr.data-checked]': 'checked()',
        '[attr.data-disabled]': 'effectiveDisabled() || null',
        '(click)': 'onToggle()',
        '(keydown)': 'onKeydown($event)',
        '(blur)': 'onTouched()',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiToggleSwitchComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiToggleSwitchComponent implements ControlValueAccessor {
    /** Disables the switch (parity with p-toggleswitch `[disabled]`). Merged with the reactive-forms disabled state. */
    readonly disabled = input(false);
    /** Forwarded onto the host `id` so an external `<label for="…">` targets the switch (parity with `[inputId]`). */
    readonly inputId = input<string>();
    /** Fires with the new boolean whenever the switch is toggled (parity with p-toggleswitch `(onChange)`). */
    readonly changed = output<boolean>();

    protected readonly checked = signal(false);
    private readonly cvaDisabled = signal(false);
    protected readonly effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected onChange: (value: boolean) => void = () => {};
    protected onTouched: () => void = () => {};

    protected readonly hostClasses = computed(() => {
        const track = this.checked() ? 'bg-tum-ui-primary' : 'bg-tum-ui-surface-300 dark:bg-tum-ui-surface-700';
        return `tum-ui-toggle-switch ${track} ${this.effectiveDisabled() ? 'opacity-60' : ''}`.trim();
    });

    protected readonly handleClasses = computed(
        () => `tum-ui-toggle-switch-handle ${this.checked() ? 'bg-tum-ui-surface-0 dark:bg-tum-ui-surface-900' : 'bg-tum-ui-surface-0 dark:bg-tum-ui-surface-400'}`,
    );

    protected onToggle(): void {
        if (this.effectiveDisabled()) {
            return;
        }
        const next = !this.checked();
        this.checked.set(next);
        this.onChange(next);
        this.onTouched();
        this.changed.emit(next);
    }

    protected onKeydown(event: KeyboardEvent): void {
        if (event.key === ' ' || event.key === 'Enter' || event.key === 'Spacebar') {
            event.preventDefault();
            this.onToggle();
        }
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
