import { ChangeDetectionStrategy, Component, computed, forwardRef, input, model, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck } from '@fortawesome/free-solid-svg-icons';

/**
 * Payload emitted by {@link TumUiCheckboxComponent.onChange}. Mirrors PrimeNG's `CheckboxChangeEvent`
 * ({@code { originalEvent, checked }}) so existing `(onChange)="handler($event.checked)"` bindings keep
 * working unchanged when a `p-checkbox` is swapped for a `tum-ui-checkbox`.
 */
export interface TumUiCheckboxChangeEvent {
    originalEvent: Event;
    checked: boolean;
}

/**
 * Binary checkbox.
 *
 * Drop-in replacement for PrimeNG's binary `p-checkbox` (`[binary]="true"`), built on a real, visually
 * hidden native `<input type="checkbox">` overlaying a token-styled box — the same structure PrimeNG uses,
 * so it keeps full keyboard support, `<label for>` association, and screen-reader semantics for free. No
 * PrimeNG / Bootstrap / CDK dependency.
 *
 * Binding contract (matches every admin usage):
 * - `[(checked)]` — two-way via the {@link checked} model signal.
 * - `[ngModel]` / `[(ngModel)]` / `(ngModelChange)` / `formControlName` — via ControlValueAccessor. This
 *   covers the admin "one-way `[ngModel]` + `(onChange)`" controlled pattern: `writeValue` drives the
 *   displayed state, and the `(onChange)` handler updates the source of truth, which flows back in.
 * - `(onChange)` — emitted on every user toggle with `{ originalEvent, checked }`, so it fires even when
 *   `[ngModel]` is bound one-way and supports both `handler()` and `handler($event.checked)`.
 *
 * Non-binary (value-collection) `p-checkbox` mode is intentionally not supported: no Artemis admin screen
 * uses it. `binary` is accepted only for template parity and the component is always boolean.
 */
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
    /** Present for `p-checkbox` template parity; the kit checkbox is always binary (boolean). */
    readonly binary = input(true);
    readonly disabled = input(false);
    /** Forwarded to the native input's `id`, so an external `<label for=…>` associates correctly. */
    readonly inputId = input<string>();
    /** Forwarded to the native input's `name`. */
    readonly name = input<string>();
    /** Accessible label for the (visually hidden) input when no `<label for>` is present. */
    readonly ariaLabel = input<string>();

    /** Checked state. Source of truth for the rendered box; supports `[(checked)]` and is driven by the CVA. */
    readonly checked = model(false);

    /** Fires on every user toggle. Named `onChange` for a drop-in swap of `p-checkbox`'s `(onChange)`. */
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
