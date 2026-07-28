import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    TemplateRef,
    ViewContainerRef,
    computed,
    effect,
    forwardRef,
    inject,
    input,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { TumUiOverlayService } from '../overlay/tum-ui-overlay.service';
import { TumUiChipComponent } from '../chip/tum-ui-chip.component';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

export interface TumUiAutoCompleteCompleteEvent {
    originalEvent?: Event;
    query: string;
}

export interface TumUiAutoCompleteSelectEvent {
    originalEvent?: Event;
    value: unknown;
}

export interface TumUiAutoCompleteUnselectEvent {
    originalEvent?: Event;
    value: unknown;
}

let nextAutoCompleteId = 0;

@Component({
    selector: 'tum-ui-autocomplete',
    templateUrl: './tum-ui-autocomplete.component.html',
    styleUrl: './tum-ui-autocomplete.component.scss',
    imports: [TumUiChipComponent, TumUiTranslatePipe],
    host: {
        '[class]': 'styleClass()',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiAutoCompleteComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiAutoCompleteComponent implements ControlValueAccessor {
    private readonly overlayService = inject(TumUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);

    readonly suggestions = input<readonly unknown[]>([]);

    readonly optionLabel = input<string>();

    readonly field = input<string>();

    readonly multiple = input(false);

    readonly placeholder = input<string>();

    readonly disabled = input(false);

    readonly minLength = input(1);

    readonly delay = input(300);

    readonly completeOnFocus = input(false);

    readonly inputId = input(`tum-ui-autocomplete-${nextAutoCompleteId++}`);
    readonly name = input<string>();
    readonly ariaLabel = input<string>();
    readonly removeAriaLabel = input<string>();
    readonly styleClass = input<string>('');
    readonly emptyMessage = input<string>();
    readonly completeMethod = output<TumUiAutoCompleteCompleteEvent>();
    readonly onSelect = output<TumUiAutoCompleteSelectEvent>();
    readonly onUnselect = output<TumUiAutoCompleteUnselectEvent>();

    protected readonly listboxId = `tum-ui-autocomplete-listbox-${nextAutoCompleteId++}`;

    private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
    private readonly textInput = viewChild<ElementRef<HTMLInputElement>>('textInput');
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
    private readonly labelKey = computed(() => this.optionLabel() ?? this.field());
    protected readonly panelVisible = computed(
        () => this.isFocused() && this.hasSearched() && !this.isDisabled() && (this.query().length >= this.minLength() || this.completeOnFocus()),
    );
    protected readonly activeOptionId = computed(() => (this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : undefined));
    protected readonly inputPlaceholder = computed(() => (this.multiple() && this.selectedValues().length > 0 ? undefined : this.placeholder()));

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
        effect(() => this.syncSingleInputText());
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
            this.textInput()?.nativeElement.focus();
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
            this.debounceTimer = setTimeout(() => this.fireComplete(value, event), this.delay());
        } else if (this.completeOnFocus()) {
            this.fireComplete(value, event);
        } else {
            this.hasSearched.set(false);
        }
    }
    private fireComplete(query: string, originalEvent?: Event): void {
        this.completeMethod.emit({ originalEvent, query });
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
        document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }

    protected selectOption(option: unknown, event?: Event): void {
        if (this.multiple()) {
            if (!this.isAlreadySelected(option)) {
                const next = [...this.selectedValues(), option];
                this.selectedValues.set(next);
                this.onChangeCallback(next);
                this.onSelect.emit({ originalEvent: event, value: option });
            }
        } else {
            this.singleValue.set(option);
            this.onChangeCallback(option);
            this.onSelect.emit({ originalEvent: event, value: option });
            this.syncSingleInputText();
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
        this.onUnselect.emit({ originalEvent: event, value: removed });
        this.focusInput();
    }

    private clearInput(): void {
        const el = this.textInput()?.nativeElement;
        if (el && this.multiple()) {
            el.value = '';
        }
        this.query.set('');
        this.hasSearched.set(false);
        this.activeIndex.set(-1);
    }
    private syncSingleInputText(): void {
        if (this.multiple()) {
            return;
        }
        const el = this.textInput()?.nativeElement;
        if (el) {
            const value = this.singleValue();
            const next = value == undefined ? '' : this.valueLabel(value);
            if (el.value !== next) {
                el.value = next;
            }
        }
    }

    private openPanel(): void {
        if (this.overlayRef) {
            return;
        }
        const origin = this.container();
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom');
        this.overlayRef.updateSize({ minWidth: origin.nativeElement.getBoundingClientRect().width });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
    }

    private closePanel(): void {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
    }
    protected optionClasses(option: unknown, index: number): string {
        const base = 'tum-ui-autocomplete-option tum:flex tum:cursor-pointer tum:items-center tum:px-3 tum:py-2';
        const active = this.activeIndex() === index;
        if (this.isAlreadySelected(option)) {
            const background = active ? 'tum:bg-tum-ui-highlight-focus-background' : 'tum:bg-tum-ui-highlight-background';
            return `${base} tum:text-tum-ui-highlight ${background}`;
        }
        const activeState = active ? ' tum:bg-tum-ui-highlight-focus-background tum:text-tum-ui-highlight' : '';
        return `${base} tum:text-tum-ui-text tum:hover:bg-tum-ui-hover-background tum:hover:text-tum-ui-text-hover${activeState}`;
    }
    protected containerClasses(): string {
        const padding = this.multiple() && this.selectedValues().length > 0 ? 'tum:p-1' : 'tum:py-1 tum:px-3';
        const base = `tum-ui-autocomplete-container tum:flex tum:w-full tum:cursor-text tum:flex-wrap tum:items-center tum:gap-1 tum:rounded-md tum:border tum:text-base tum:transition-colors ${padding}`;
        let state: string;
        if (this.isDisabled()) {
            state = 'tum:bg-tum-ui-disabled-background tum:text-tum-ui-disabled tum:border-tum-ui-control-border';
        } else if (this.isFocused()) {
            state = 'tum:bg-tum-ui-control-background tum:text-tum-ui-text tum:border-tum-ui-primary';
        } else {
            state = 'tum:bg-tum-ui-control-background tum:text-tum-ui-text tum:border-tum-ui-control-border tum:hover:border-tum-ui-control-border-hover';
        }
        return `${base} ${state}`;
    }
}
