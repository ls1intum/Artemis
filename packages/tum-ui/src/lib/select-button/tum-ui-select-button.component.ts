import { ChangeDetectionStrategy, Component, TemplateRef, booleanAttribute, computed, forwardRef, input, output, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export type TumUiSelectButtonOption = unknown;

export type TumUiSelectButtonSize = 'small' | 'large';

interface NormalizedOption {
    readonly raw: TumUiSelectButtonOption;

    readonly value: unknown;

    readonly label: string;

    readonly selected: boolean;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === 'object';
}

function displayLabel(value: unknown): string | undefined {
    return typeof value === 'string' || typeof value === 'number' || typeof value === 'bigint' || typeof value === 'boolean' ? String(value) : undefined;
}

/** ControlValueAccessor for choosing one value from a small, persistent option set. */
@Component({
    selector: 'tum-ui-select-button',
    templateUrl: './tum-ui-select-button.component.html',
    styleUrl: './tum-ui-select-button.component.scss',
    imports: [NgTemplateOutlet],
    host: {
        role: 'group',
        class: 'tum-ui-select-button',
        '[attr.aria-disabled]': 'effectiveDisabled() || null',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectButtonComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiSelectButtonComponent implements ControlValueAccessor {
    /** Options that yield neither a primitive label nor an item template are omitted. */
    readonly options = input<readonly TumUiSelectButtonOption[]>([]);

    /** Object property used as the visible option label. */
    readonly optionLabel = input<string>();

    /** Object property written to the form value; omit it to write the option. */
    readonly optionValue = input<string>();
    readonly size = input<TumUiSelectButtonSize>();

    /** Allows the selected option to be toggled back to `undefined`. */
    readonly allowEmpty = input(true, { transform: booleanAttribute });
    readonly disabled = input(false, { transform: booleanAttribute });

    /** Optional presentation template; the option remains its implicit context value. */
    readonly itemTemplate = input<TemplateRef<{ $implicit: TumUiSelectButtonOption }>>();

    /** Emits the selected value, or `undefined` when cleared. */
    readonly changed = output<unknown>();

    private readonly value = signal<unknown>(undefined);
    private readonly cvaDisabled = signal(false);
    protected readonly effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled());

    protected onChange: (value: unknown) => void = () => {};
    protected onTouched: () => void = () => {};

    protected readonly normalizedOptions = computed<NormalizedOption[]>(() => {
        const labelKey = this.optionLabel();
        const valueKey = this.optionValue();
        const current = this.value();
        return this.options().flatMap((raw) => {
            const record = isRecord(raw) ? raw : undefined;
            if ((labelKey !== undefined || valueKey !== undefined) && !record) {
                return [];
            }
            const value = valueKey !== undefined ? record![valueKey] : raw;
            const labelValue = labelKey !== undefined ? record![labelKey] : raw;
            const label = displayLabel(labelValue);
            return label === undefined && !this.itemTemplate() ? [] : [{ raw, value, label: label ?? '', selected: value === current }];
        });
    });

    protected optionClasses(selected: boolean): string {
        const sizeClass = this.size() === 'small' ? 'tum:text-sm' : this.size() === 'large' ? 'tum:text-lg' : 'tum:text-base';
        const state = selected ? 'tum:bg-primary tum:text-primary-contrast tum:border-primary' : 'tum:bg-hover-background tum:text-text tum:border-border';
        return `tum-ui-select-button-option ${sizeClass} ${state} ${this.effectiveDisabled() ? 'tum:opacity-60' : ''}`.trim();
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
