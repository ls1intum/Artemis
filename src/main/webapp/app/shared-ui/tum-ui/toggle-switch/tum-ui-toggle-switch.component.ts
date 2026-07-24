import { ChangeDetectionStrategy, Component, computed, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Owned boolean on/off switch, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-toggleswitch`: same 2.5rem × 1.5rem pill track with a sliding
 * 1rem handle, reproduced from the Aura `toggleswitch` tokens + base style. `bg-primary` when on, the
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

    // Track color rides the surface ramp when off and `bg-primary` when on; the handle color lives on the
    // inner span (see the template). Disabled is expressed with the kit's opacity convention (like tum-ui-button)
    // rather than Aura's discrete disabled fill, so it stays consistent across the kit.
    protected readonly hostClasses = computed(() => {
        const track = this.checked() ? 'bg-primary' : 'bg-surface-300 dark:bg-surface-700';
        return `tum-ui-toggle-switch ${track} ${this.effectiveDisabled() ? 'opacity-60' : ''}`.trim();
    });

    // In light mode the handle is white on both states; in dark mode Aura darkens the checked handle
    // (surface.400 off → surface.900 on), which we reproduce with the surface ramp.
    protected readonly handleClasses = computed(() => `tum-ui-toggle-switch-handle ${this.checked() ? 'bg-surface-0 dark:bg-surface-900' : 'bg-surface-0 dark:bg-surface-400'}`);

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
        // The host is not a native <button>, so Space / Enter never synthesize a click — handle them
        // explicitly (and preventDefault on Space to stop the page from scrolling).
        if (event.key === ' ' || event.key === 'Enter' || event.key === 'Spacebar') {
            event.preventDefault();
            this.onToggle();
        }
    }

    // ControlValueAccessor
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
