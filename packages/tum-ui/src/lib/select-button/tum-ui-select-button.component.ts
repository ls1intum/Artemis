import { ChangeDetectionStrategy, Component, ElementRef, TemplateRef, computed, forwardRef, input, output, signal, viewChildren } from '@angular/core';
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

    readonly optionLabel = input<string>();

    readonly optionValue = input<string>();
    readonly size = input<TumUiSelectButtonSize>();

    readonly allowEmpty = input(true);
    readonly disabled = input(false);

    readonly itemTemplate = input<TemplateRef<{ $implicit: TumUiSelectButtonOption }>>();

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
        const sizeClass = this.size() === 'small' ? 'tum:text-sm' : this.size() === 'large' ? 'tum:text-lg' : 'tum:text-base';
        const state = selected
            ? 'tum:bg-tum-ui-primary tum:text-tum-ui-primary-contrast tum:border-tum-ui-primary'
            : 'tum:bg-tum-ui-hover-background tum:text-tum-ui-text tum:border-tum-ui-border';
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
