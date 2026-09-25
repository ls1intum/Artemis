import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    TemplateRef,
    ViewContainerRef,
    booleanAttribute,
    computed,
    effect,
    forwardRef,
    inject,
    input,
    numberAttribute,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { DOCUMENT } from '@angular/common';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { TumAetUiOverlayService } from '../overlay/tumaet-ui-overlay.service';
import { TumAetUiChipComponent } from '../chip/tumaet-ui-chip.component';
import { TumAetUiTranslatePipe } from '../i18n/tumaet-ui-translate.pipe';

export interface TumAetUiAutoCompleteSearchEvent {
    originalEvent?: Event;
    query: string;
}

export interface TumAetUiAutoCompleteOptionEvent {
    originalEvent?: Event;
    value: unknown;
}

let nextAutoCompleteId = 0;

/** Single- or multi-value ControlValueAccessor with consumer-supplied suggestions. */
@Component({
    selector: 'tumaet-ui-autocomplete',
    templateUrl: './tumaet-ui-autocomplete.component.html',
    styleUrl: './tumaet-ui-autocomplete.component.scss',
    imports: [TumAetUiChipComponent, TumAetUiTranslatePipe],
    host: {
        class: 'tumaet-ui-autocomplete',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumAetUiAutoCompleteComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiAutoCompleteComponent implements ControlValueAccessor {
    private readonly overlayService = inject(TumAetUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);
    private readonly document = inject(DOCUMENT);

    /** Suggestions supplied in response to a search request. */
    readonly suggestions = input<readonly unknown[]>([]);

    /** Property name used as the visible label for object values. */
    readonly optionLabel = input<string>();

    readonly multiple = input(false, { transform: booleanAttribute });

    readonly placeholder = input<string>();

    readonly disabled = input(false, { transform: booleanAttribute });

    /** Minimum query length before a search request emits. */
    readonly minLength = input(1, { transform: numberAttribute });

    /** Delay between the latest input and a search request. */
    readonly debounceMs = input(300, { transform: numberAttribute });

    /** Requests suggestions when the empty input receives focus. */
    readonly completeOnFocus = input(false, { transform: booleanAttribute });

    readonly inputId = input(`tumaet-ui-autocomplete-${nextAutoCompleteId++}`);
    readonly name = input<string>();
    readonly ariaLabel = input<string>();
    readonly removeAriaLabel = input<string>();
    /** Message shown when a completed search returns no suggestions. */
    readonly emptyMessage = input<string>();
    /** Requests suggestions for the current text query. */
    readonly searchRequested = output<TumAetUiAutoCompleteSearchEvent>();
    readonly optionSelected = output<TumAetUiAutoCompleteOptionEvent>();
    readonly optionRemoved = output<TumAetUiAutoCompleteOptionEvent>();

    protected readonly listboxId = `tumaet-ui-autocomplete-listbox-${nextAutoCompleteId++}`;

    private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
    private readonly textInput = viewChild.required<ElementRef<HTMLInputElement>>('textInput');
    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;

    protected readonly selectedValues = signal<unknown[]>([]);
    private readonly singleValue = signal<unknown>(undefined);
    protected readonly query = signal('');
    protected readonly isFocused = signal(false);
    private readonly hasSearched = signal(false);
    protected readonly activeIndex = signal(-1);
    private readonly disabledByForm = signal(false);

    private debounceTimer?: ReturnType<typeof setTimeout>;
    private onChangeCallback: (value: unknown) => void = () => {};
    private onTouchedCallback: () => void = () => {};

    protected readonly isDisabled = computed(() => this.disabled() || this.disabledByForm());
    private readonly labelKey = computed(() => this.optionLabel());
    protected readonly panelVisible = computed(
        () => this.isFocused() && this.hasSearched() && !this.isDisabled() && (this.query().length >= this.minLength() || this.completeOnFocus()),
    );
    protected readonly activeOptionId = computed(() => (this.panelVisible() && this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : undefined));
    protected readonly inputPlaceholder = computed(() => (this.multiple() && this.selectedValues().length > 0 ? undefined : this.placeholder()));
    protected readonly inputText = computed(() => {
        if (this.multiple()) {
            return this.query();
        }
        const value = this.singleValue();
        return value == undefined ? '' : this.valueLabel(value);
    });

    constructor() {
        this.destroyRef.onDestroy(() => {
            this.overlayRef?.dispose();
            if (this.debounceTimer) {
                clearTimeout(this.debounceTimer);
            }
        });
        effect(() => {
            if (this.panelVisible()) {
                this.openPanel();
            } else {
                this.closePanel();
            }
        });
        effect(() => {
            const optionCount = this.suggestions().length;
            if (this.activeIndex() >= optionCount) {
                this.activeIndex.set(optionCount > 0 ? optionCount - 1 : -1);
            }
        });
    }

    writeValue(value: unknown): void {
        if (this.multiple()) {
            this.selectedValues.set(Array.isArray(value) ? [...value] : value == undefined ? [] : [value]);
        } else {
            this.singleValue.set(value ?? undefined);
        }
    }

    registerOnChange(fn: (value: unknown) => void): void {
        this.onChangeCallback = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onTouchedCallback = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.disabledByForm.set(isDisabled);
    }
    protected valueLabel(value: unknown): string {
        const key = this.labelKey();
        const raw = key && value !== null && typeof value === 'object' ? (value as Record<string, unknown>)[key] : value;
        return this.toText(raw);
    }

    private toText(value: unknown): string {
        switch (typeof value) {
            case 'string':
                return value;
            case 'number':
            case 'boolean':
            case 'bigint':
                return String(value);
            default:
                return '';
        }
    }

    private valuesMatch(a: unknown, b: unknown): boolean {
        return Object.is(a, b) || a === b;
    }

    protected isAlreadySelected(option: unknown): boolean {
        return this.selectedValues().some((value) => this.valuesMatch(value, option));
    }

    protected optionId(index: number): string {
        return `${this.listboxId}-option-${index}`;
    }

    protected focusInput(): void {
        if (!this.isDisabled()) {
            this.textInput().nativeElement.focus();
        }
    }

    protected onFocus(event: FocusEvent): void {
        this.isFocused.set(true);
        if (this.completeOnFocus() && !this.isDisabled()) {
            this.fireComplete(this.query(), event);
        }
    }

    protected onBlur(): void {
        this.isFocused.set(false);
        this.onTouchedCallback();
    }

    protected onInput(event: Event): void {
        const value = (event.target as HTMLInputElement).value;
        this.query.set(value);
        this.activeIndex.set(-1);
        if (!this.multiple()) {
            const singleVal = value === '' ? undefined : value;
            this.singleValue.set(singleVal);
            this.onChangeCallback(singleVal);
        }
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }
        if (value.length >= this.minLength()) {
            this.debounceTimer = setTimeout(() => this.fireComplete(value, event), this.debounceMs());
        } else if (this.completeOnFocus()) {
            this.fireComplete(value, event);
        } else {
            this.hasSearched.set(false);
        }
    }
    private fireComplete(query: string, originalEvent?: Event): void {
        this.searchRequested.emit({ originalEvent, query });
        this.hasSearched.set(true);
    }

    protected onInputKeydown(event: KeyboardEvent): void {
        const count = this.suggestions().length;
        switch (event.key) {
            case 'ArrowDown':
                if (this.panelVisible() && count > 0) {
                    event.preventDefault();
                    this.setActive(Math.min(count - 1, this.activeIndex() + 1));
                }
                break;
            case 'ArrowUp':
                if (this.panelVisible() && count > 0) {
                    event.preventDefault();
                    this.setActive(Math.max(0, this.activeIndex() - 1));
                }
                break;
            case 'Enter':
                if (this.panelVisible() && this.activeIndex() >= 0 && this.activeIndex() < count) {
                    event.preventDefault();
                    this.selectOption(this.suggestions()[this.activeIndex()], event);
                }
                break;
            case 'Escape':
                if (this.panelVisible()) {
                    event.stopPropagation();
                    this.hasSearched.set(false);
                }
                break;
            case 'Backspace':
                if (this.multiple() && this.query().length === 0 && this.selectedValues().length > 0) {
                    this.removeAt(this.selectedValues().length - 1, event);
                }
                break;
        }
    }

    protected setActive(index: number): void {
        this.activeIndex.set(index);
        this.document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }

    protected selectOption(option: unknown, event?: Event): void {
        if (this.multiple()) {
            if (!this.isAlreadySelected(option)) {
                const next = [...this.selectedValues(), option];
                this.selectedValues.set(next);
                this.onChangeCallback(next);
                this.optionSelected.emit({ originalEvent: event, value: option });
            }
        } else {
            this.singleValue.set(option);
            this.onChangeCallback(option);
            this.optionSelected.emit({ originalEvent: event, value: option });
        }
        this.clearInput();
        this.focusInput();
    }

    protected removeAt(index: number, event?: Event): void {
        const current = this.selectedValues();
        if (index < 0 || index >= current.length) {
            return;
        }
        const removed = current[index];
        const next = current.filter((_, i) => i !== index);
        this.selectedValues.set(next);
        this.onChangeCallback(next);
        this.optionRemoved.emit({ originalEvent: event, value: removed });
        this.focusInput();
    }

    private clearInput(): void {
        this.query.set('');
        this.hasSearched.set(false);
        this.activeIndex.set(-1);
    }

    private openPanel(): void {
        if (this.overlayRef) {
            return;
        }
        const origin = this.container();
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom', { matchOriginWidth: true });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
    }

    private closePanel(): void {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
    }
    protected optionClasses(option: unknown, index: number): string {
        const base = 'tumaet-ui-autocomplete-option tumaet:flex tumaet:cursor-pointer tumaet:items-center tumaet:px-3 tumaet:py-2';
        const active = this.activeIndex() === index;
        if (this.isAlreadySelected(option)) {
            const background = active ? 'tumaet:bg-highlight-focus-background' : 'tumaet:bg-highlight-background';
            return `${base} tumaet:text-highlight ${background}`;
        }
        const activeState = active ? ' tumaet:bg-highlight-focus-background tumaet:text-highlight' : '';
        return `${base} tumaet:text-text tumaet:hover:bg-hover-background tumaet:hover:text-text-hover${activeState}`;
    }
    protected containerClasses(): string {
        const padding = this.multiple() && this.selectedValues().length > 0 ? 'tumaet:p-1' : 'tumaet:py-1 tumaet:px-3';
        const base =
            `tumaet-ui-autocomplete-container tumaet:box-border tumaet:flex tumaet:w-full tumaet:cursor-text tumaet:flex-wrap tumaet:items-center tumaet:gap-1 tumaet:rounded-md tumaet:border tumaet:text-base tumaet:transition-colors ` +
            `tumaet:focus-within:outline tumaet:focus-within:outline-2 tumaet:focus-within:outline-focus tumaet:focus-within:outline-offset-2 ${padding}`;
        let state: string;
        if (this.isDisabled()) {
            state = 'tumaet:bg-disabled-background tumaet:text-disabled tumaet:border-control-border';
        } else if (this.isFocused()) {
            state = 'tumaet:bg-control-background tumaet:text-text tumaet:border-focus';
        } else {
            state = 'tumaet:bg-control-background tumaet:text-text tumaet:border-control-border tumaet:hover:border-control-border-hover';
        }
        return `${base} ${state}`;
    }
}
