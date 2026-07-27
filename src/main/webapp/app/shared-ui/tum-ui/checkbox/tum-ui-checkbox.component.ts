import { ChangeDetectionStrategy, Component, computed, forwardRef, input, model, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faMinus } from '@fortawesome/free-solid-svg-icons';

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
 * Owned binary checkbox, part of the tum-aet-ui kit (future @tumaet/ui-angular).
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
 * - `[indeterminate]` — drop-in for `p-checkbox [indeterminate]`: shows a filled box with a dash instead of
 *   the tick (e.g. a select-all header when only some rows are selected). Presentation only — it never
 *   changes `checked`, the model, or what `(onChange)` emits.
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
    /**
     * Renders the partial-selection dash instead of the tick (drop-in for `p-checkbox [indeterminate]`). Purely
     * visual: it fills the box and swaps the glyph but never touches `checked`, the model, or `onChange`.
     */
    readonly indeterminate = input(false);
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
    protected readonly faMinus = faMinus;

    // Indeterminate takes visual precedence over checked (as a native `<input indeterminate>` does): the dash
    // shows whenever indeterminate is set, the tick only when checked and NOT indeterminate.
    protected readonly showDash = computed(() => this.indeterminate());
    protected readonly showTick = computed(() => this.checked() && !this.indeterminate());

    // `disabled` can arrive either as an input or from a reactive form (setDisabledState); either disables it.
    private readonly cvaDisabled = signal(false);
    protected readonly isDisabled = computed(() => this.disabled() || this.cvaDisabled());

    // Box color set (bg/border/text) via sanctioned token utilities, toggled by state — same approach as
    // tum-ui-button.variants. Structure, hover overlay, and focus ring live in the stylesheet (`:has()`).
    protected readonly boxClasses = computed(() => {
        if (this.isDisabled()) {
            return 'bg-surface-200 border-surface-300 dark:bg-surface-700 dark:border-surface-600';
        }
        // Both the checked tick and the indeterminate dash sit on a brand-filled box, matching Aura.
        if (this.checked() || this.indeterminate()) {
            return 'bg-primary border-primary';
        }
        return 'bg-surface-0 border-surface-300 dark:bg-surface-950 dark:border-surface-600';
    });

    // Aura tints a disabled (but checked) tick with the muted form-field color rather than the contrast color.
    protected readonly iconClasses = computed(() => (this.isDisabled() ? 'text-surface-500 dark:text-surface-400' : 'text-surface-0'));

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
