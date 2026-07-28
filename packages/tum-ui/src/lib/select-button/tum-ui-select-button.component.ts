import { ChangeDetectionStrategy, Component, ElementRef, TemplateRef, computed, forwardRef, input, output, signal, viewChildren } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** One entry of the {@link TumUiSelectButtonComponent} `options` list; objects or primitives are both accepted. */
export type TumUiSelectButtonOption = unknown;

/** Optional size scale, matching p-selectbutton's `size` (default = normal). */
export type TumUiSelectButtonSize = 'small' | 'large';

interface NormalizedOption {
    /** The original option object / primitive — passed as `$implicit` to a custom item template. */
    readonly raw: TumUiSelectButtonOption;
    /** The model value the option contributes (via `optionValue`, else the raw option). */
    readonly value: unknown;
    /** The visible text (via `optionLabel`, else the raw option stringified). */
    readonly label: string;
    /** Whether this option is the currently selected one. */
    readonly selected: boolean;
}

/**
 * Segmented single-select control.
 *
 * Drop-in replacement for PrimeNG's single-select `p-selectbutton`: a row of joined buttons built from
 * `options`, with `optionLabel` / `optionValue` accessors and an optional custom item template (the
 * equivalent of p-selectbutton's `#item` / `pTemplate("item")`). Structure / sizing come from the Aura
 * `togglebutton` tokens (0.5rem × 1rem padding, joined borders, `--radius-md` first/last corners); the
 * selected segment is highlighted with `bg-tum-ui-primary` per the kit house style, dark-mode-correct for free.
 *
 * Semantics follow the WAI-ARIA radio-group pattern: the host is a `role="radiogroup"`, each option a
 * `role="radio"` button, arrow keys roam + select, and Space / Enter activate (native to `<button>`).
 * Implements `ControlValueAccessor`, so `[(ngModel)]`, one-way `[ngModel]` + `(ngModelChange)`, and
 * reactive `formControlName` all work.
 *
 * Multiple selection is intentionally out of scope: no admin usage needs it (grep of `p-selectbutton`),
 * and it would require a different aria model (aria-pressed toggle buttons rather than a radiogroup).
 */
@Component({
    selector: 'tum-ui-select-button',
    templateUrl: './tum-ui-select-button.component.html',
    styleUrl: './tum-ui-select-button.component.scss',
    imports: [NgTemplateOutlet],
    host: {
        role: 'radiogroup',
        class: 'tum-ui-select-button',
        '[attr.aria-disabled]': 'effectiveDisabled() || null',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectButtonComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiSelectButtonComponent implements ControlValueAccessor {
    readonly options = input<readonly TumUiSelectButtonOption[]>([]);
    /** Object property to read the visible label from (parity with p-selectbutton `optionLabel`). */
    readonly optionLabel = input<string>();
    /** Object property to read the model value from (parity with p-selectbutton `optionValue`). */
    readonly optionValue = input<string>();
    readonly size = input<TumUiSelectButtonSize>();
    /** When false, clicking the selected option keeps it selected (parity with `[allowEmpty]="false"`). */
    readonly allowEmpty = input(true);
    readonly disabled = input(false);
    /** Custom per-option template; receives the raw option as `$implicit` (equivalent to p-selectbutton `#item`). */
    readonly itemTemplate = input<TemplateRef<{ $implicit: TumUiSelectButtonOption }>>();
    /** Fires with the new value whenever the selection changes (parity with p-selectbutton `(onChange)`). */
    readonly changed = output<unknown>();

    private readonly optionButtons = viewChildren<ElementRef<HTMLButtonElement>>('optionButton');

    private readonly value = signal<unknown>(undefined);
    private readonly cvaDisabled = signal(false);
    protected readonly effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected onChange: (value: unknown) => void = () => {};
    protected onTouched: () => void = () => {};

    protected readonly normalizedOptions = computed<NormalizedOption[]>(() => {
        const labelKey = this.optionLabel();
        const valueKey = this.optionValue();
        const current = this.value();
        return this.options().map((raw) => {
            const value = valueKey != undefined ? (raw as Record<string, unknown>)[valueKey] : raw;
            const label = labelKey != undefined ? String((raw as Record<string, unknown>)[labelKey]) : String(raw);
            return { raw, value, label, selected: value === current };
        });
    });

    protected readonly focusableIndex = computed(() => {
        const index = this.normalizedOptions().findIndex((option) => option.selected);
        return index >= 0 ? index : 0;
    });

    protected optionClasses(selected: boolean): string {
        const sizeClass = this.size() === 'small' ? 'text-sm' : this.size() === 'large' ? 'text-lg' : 'text-base';
        const state = selected
            ? 'bg-tum-ui-primary text-tum-ui-surface-0 border-tum-ui-primary'
            : 'bg-tum-ui-surface-100 text-tum-ui-muted border-tum-ui-surface-200 dark:bg-tum-ui-surface-800 dark:border-tum-ui-surface-700';
        return `tum-ui-select-button-option ${sizeClass} ${state} ${this.effectiveDisabled() ? 'opacity-60' : ''}`.trim();
    }

    protected select(option: NormalizedOption): void {
        if (this.effectiveDisabled()) {
            return;
        }
        let next: unknown;
        if (option.selected) {
            if (!this.allowEmpty()) {
                return;
            }
            next = undefined;
        } else {
            next = option.value;
        }
        this.value.set(next);
        this.onChange(next);
        this.onTouched();
        this.changed.emit(next);
    }

    protected onKeydown(event: KeyboardEvent, index: number): void {
        if (this.effectiveDisabled()) {
            return;
        }
        const options = this.normalizedOptions();
        const count = options.length;
        if (count === 0) {
            return;
        }
        let target: number;
        switch (event.key) {
            case 'ArrowRight':
            case 'ArrowDown':
                target = (index + 1) % count;
                break;
            case 'ArrowLeft':
            case 'ArrowUp':
                target = (index - 1 + count) % count;
                break;
            case 'Home':
                target = 0;
                break;
            case 'End':
                target = count - 1;
                break;
            default:
                return;
        }
        event.preventDefault();
        if (target !== index) {
            this.select(options[target]);
        }
        this.optionButtons()[target]?.nativeElement.focus();
    }

    writeValue(value: unknown): void {
        this.value.set(value ?? undefined);
    }

    registerOnChange(fn: (value: unknown) => void): void {
        this.onChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.cvaDisabled.set(isDisabled);
    }
}
