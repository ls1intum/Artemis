import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    Injector,
    TemplateRef,
    ViewContainerRef,
    afterRenderEffect,
    booleanAttribute,
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
import { DOCUMENT } from '@angular/common';
import { OverlayRef } from '@angular/cdk/overlay';
import { ListKeyManager } from '@angular/cdk/a11y';
import { TemplatePortal } from '@angular/cdk/portal';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faChevronDown, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiOverlayService } from '../overlay/tum-ui-overlay.service';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';
import { TUM_UI_FORM_FIELD } from '../form-field/tum-ui-form-field.token';

export type TumUiSelectSize = 'small' | 'large';
const TRIGGER_SIZE: Record<'small' | 'default' | 'large', string> = {
    small: 'tum:min-h-8 tum:py-1.5 tum:ps-2.5 tum:text-sm',
    default: 'tum:min-h-10 tum:py-2 tum:ps-3 tum:text-base',
    large: 'tum:min-h-12 tum:py-2.5 tum:ps-3.5 tum:text-lg',
};

let nextSelectId = 0;
const TYPEAHEAD_DEBOUNCE_MS = 500;

/** Single-value ControlValueAccessor backed by a listbox overlay. */
@Component({
    selector: 'tum-ui-select',
    templateUrl: './tum-ui-select.component.html',
    styleUrl: './tum-ui-select.component.scss',
    imports: [FaIconComponent, TumUiTranslatePipe],
    host: {
        // The application stylesheet excludes TUM UI controls from the JHipster validity accent by this class.
        class: 'tum-ui-select',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiSelectComponent implements ControlValueAccessor {
    private readonly overlayService = inject(TumUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);
    private readonly document = inject(DOCUMENT);
    private readonly injector = inject(Injector);
    private readonly formField = inject(TUM_UI_FORM_FIELD, { optional: true });

    readonly options = input<readonly unknown[]>([]);

    /** Property name used as the visible label for object options. */
    readonly optionLabel = input<string>();

    /** Property name written to the form value; omit it to write the option itself. */
    readonly optionValue = input<string>();

    readonly placeholder = input<string>();

    readonly disabled = input(false, { transform: booleanAttribute });

    readonly showClear = input(false, { transform: booleanAttribute });

    /** Adds a search field above the option list, for option sets too long to scan. */
    readonly filter = input(false, { transform: booleanAttribute });

    /**
     * Comma-separated property names searched by the filter, for object options whose match should not be
     * limited to the visible label — `"name,login"`, say. Defaults to the label alone.
     */
    readonly filterBy = input<string>();

    readonly filterPlaceholder = input<string>();

    readonly size = input<TumUiSelectSize>();

    /**
     * `id` of the trigger, so an external `<label for>` associates. Defaults to the id of an enclosing
     * `tum-ui-form-field`, and to a unique per-instance id outside one.
     */
    readonly inputId = input<string>();
    readonly name = input<string>();
    readonly ariaLabel = input<string>();
    readonly clearAriaLabel = input<string>();
    readonly emptyMessage = input<string>();
    readonly filterAriaLabel = input<string>();
    readonly selectionChange = output<unknown>();

    protected readonly faChevronDown = faChevronDown;
    protected readonly faCheck = faCheck;
    protected readonly faXmark = faXmark;

    private readonly fallbackInputId = `tum-ui-select-${nextSelectId++}`;
    protected readonly resolvedInputId = computed(() => this.formField?.explicitControlId() ?? this.inputId() ?? this.formField?.labelTargetId() ?? this.fallbackInputId);
    protected readonly describedBy = computed(() => this.formField?.describedBy() ?? null);
    protected readonly isInvalid = computed(() => this.formField?.invalid() ?? false);

    protected readonly listboxId = `tum-ui-select-listbox-${nextSelectId++}`;

    private readonly trigger = viewChild.required<ElementRef<HTMLElement>>('trigger');
    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private readonly filterInput = viewChild<ElementRef<HTMLInputElement>>('filterInput');
    private overlayRef?: OverlayRef;

    protected readonly isOpen = signal(false);
    protected readonly activeIndex = signal(-1);
    protected readonly filterText = signal('');
    private readonly selectedValue = signal<unknown>(undefined);
    private readonly disabledByForm = signal(false);

    private onChangeCallback: (value: unknown) => void = () => {};
    private onTouchedCallback: () => void = () => {};

    protected readonly isDisabled = computed(() => this.disabled() || this.disabledByForm());
    protected readonly selectedOption = computed(() => {
        const current = this.selectedValue();
        if (current === undefined || current === null) {
            return undefined;
        }
        return this.options().find((option) => this.valuesMatch(this.resolveValue(option), current));
    });

    /**
     * The options the panel shows. Everything index-based - the key manager, `aria-activedescendant`, the
     * option ids and every keyboard action - runs over this list rather than `options()`, so an index can
     * never point at an option the user cannot see.
     */
    /** Whether a query is currently narrowing the list, rather than merely present. */
    protected readonly isFiltering = computed(() => this.filter() && this.filterText().trim().length > 0);

    protected readonly visibleOptions = computed(() => {
        if (!this.isFiltering()) {
            return this.options();
        }
        const query = this.filterText().trim().toLocaleLowerCase();
        return this.options().filter((option) => this.filterFields(option).some((field) => field.toLocaleLowerCase().includes(query)));
    });

    protected readonly hasSelection = computed(() => this.selectedOption() !== undefined);
    protected readonly displayLabel = computed(() => {
        const option = this.selectedOption();
        return option !== undefined ? this.label(option) : (this.placeholder() ?? '');
    });
    protected readonly showClearButton = computed(() => this.showClear() && this.hasSelection() && !this.isDisabled());
    protected readonly triggerClasses = computed(() => `${this.buildTriggerClasses()} ${this.showClearButton() ? 'tum:pe-17' : 'tum:pe-10'}`);
    protected readonly activeOptionId = computed(() => (this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : undefined));
    private readonly keyManagerOptions = computed(() => this.visibleOptions().map((option) => ({ getLabel: () => this.label(option) })));
    private readonly keyManager = new ListKeyManager(this.keyManagerOptions, this.injector).withVerticalOrientation().withHomeAndEnd().withTypeAhead(TYPEAHEAD_DEBOUNCE_MS);
    private typeaheadSequence = '';
    private pendingFilterFocus = false;
    private typeaheadReset?: ReturnType<typeof setTimeout>;

    constructor() {
        // Tell an enclosing field which id to label whenever this control was given one of its own.
        effect(() => {
            const ownId = this.inputId();
            if (ownId) {
                this.formField?.adoptControlId(ownId);
            }
        });
        this.destroyRef.onDestroy(() => {
            this.overlayRef?.dispose();
            this.resetTypeahead();
            this.keyManager.destroy();
        });
        this.keyManager.change.subscribe((index) => {
            this.activeIndex.set(index);
            this.scrollOptionIntoView(index);
        });
        effect(() => {
            if (this.isDisabled()) {
                this.close();
            }
        });
        // Focus lands on the search field when one is shown, so typing filters the list rather than running
        // the trigger's typeahead.
        afterRenderEffect(() => {
            const field = this.filterInput()?.nativeElement;
            if (this.pendingFilterFocus && field) {
                this.pendingFilterFocus = false;
                field.focus();
            }
        });
        effect(() => {
            const optionCount = this.visibleOptions().length;
            if (optionCount === 0) {
                this.keyManager.setActiveItem(-1);
            } else if (this.activeIndex() >= optionCount) {
                this.keyManager.setActiveItem(optionCount - 1);
            } else if (this.isOpen() && this.activeIndex() < 0) {
                this.keyManager.setFirstItemActive();
            }
        });
    }

    writeValue(value: unknown): void {
        this.selectedValue.set(value ?? undefined);
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
    protected label(option: unknown): string {
        const key = this.optionLabel();
        const raw = key && option !== null && typeof option === 'object' ? (option as Record<string, unknown>)[key] : option;
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
    /** The strings the filter searches for one option: the named `filterBy` fields, or the visible label. */
    private filterFields(option: unknown): string[] {
        const keys = this.filterBy()
            ?.split(',')
            .map((key) => key.trim())
            .filter((key) => key.length > 0);
        if (!keys?.length || option === null || typeof option !== 'object') {
            return [this.label(option)];
        }
        return keys.map((key) => this.toText((option as Record<string, unknown>)[key]));
    }

    private resolveValue(option: unknown): unknown {
        const key = this.optionValue();
        if (key && option !== null && typeof option === 'object') {
            return (option as Record<string, unknown>)[key];
        }
        return option;
    }

    private valuesMatch(a: unknown, b: unknown): boolean {
        return Object.is(a, b) || a === b;
    }

    protected isSelected(option: unknown): boolean {
        const current = this.selectedValue();
        if (current === undefined || current === null) {
            return false;
        }
        return this.valuesMatch(this.resolveValue(option), current);
    }

    protected optionId(index: number): string {
        return `${this.listboxId}-option-${index}`;
    }

    protected toggle(): void {
        if (this.isDisabled()) {
            return;
        }
        if (this.isOpen()) {
            this.close();
        } else {
            this.open();
        }
    }

    private open(): void {
        if (this.isOpen() || this.isDisabled()) {
            return;
        }
        const selectedIndex = this.visibleOptions().findIndex((option) => this.isSelected(option));
        const initialIndex = selectedIndex >= 0 ? selectedIndex : this.visibleOptions().length > 0 ? 0 : -1;
        this.keyManager.setActiveItem(initialIndex);
        const origin = this.trigger();
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom', { hasBackdrop: true, matchOriginWidth: true });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.scrollOptionIntoView(initialIndex);
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.isOpen.set(true);
        // The portal attaches above, but its input is only in the document once the view has been rendered,
        // so the focus move is deferred to the render effect in the constructor.
        this.pendingFilterFocus = this.filter();
    }

    private close(restoreFocus = true): void {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.isOpen.set(false);
        this.filterText.set('');
        this.resetTypeahead();
        this.onTouchedCallback();
        if (restoreFocus && !this.isDisabled()) {
            this.trigger().nativeElement.focus();
        }
    }

    protected selectOption(option: unknown): void {
        const value = this.resolveValue(option);
        this.selectedValue.set(value);
        this.onChangeCallback(value);
        this.selectionChange.emit(value);
        this.close();
    }

    protected clear(event: MouseEvent): void {
        event.stopPropagation();
        this.selectedValue.set(undefined);
        this.onChangeCallback(undefined);
        this.selectionChange.emit(undefined);
        this.onTouchedCallback();
        this.trigger().nativeElement.focus();
    }

    protected setActive(index: number): void {
        this.keyManager.setActiveItem(index);
    }

    protected onTriggerKeydown(event: KeyboardEvent): void {
        if (this.isDisabled()) {
            return;
        }
        if (!this.isOpen()) {
            if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
                event.preventDefault();
                this.open();
                return;
            }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                this.open();
                return;
            }
            if (event.key === 'Home' || event.key === 'End') {
                event.preventDefault();
                this.open();
                this.setActive(event.key === 'Home' ? 0 : this.visibleOptions().length - 1);
                return;
            }
            if (event.key.length === 1 && event.key !== ' ' && !event.ctrlKey && !event.metaKey && !event.altKey) {
                this.open();
                // With a search field the character belongs in it, and focus is moving there anyway.
                if (!this.filter()) {
                    this.handleTypeahead(event);
                }
            }
            return;
        }
        const count = this.visibleOptions().length;
        switch (event.key) {
            case 'Enter':
            case ' ':
            case 'Spacebar':
                event.preventDefault();
                if (this.activeIndex() >= 0 && this.activeIndex() < count) {
                    this.selectOption(this.visibleOptions()[this.activeIndex()]);
                }
                break;
            case 'Escape':
                this.close();
                break;
            case 'Tab':
                if (this.activeIndex() >= 0 && this.activeIndex() < count) {
                    this.selectOption(this.visibleOptions()[this.activeIndex()]);
                } else {
                    this.close(false);
                }
                break;
            default:
                if (event.key.length === 1 && event.key !== ' ' && !event.ctrlKey && !event.metaKey && !event.altKey) {
                    this.handleTypeahead(event);
                } else {
                    this.keyManager.onKeydown(event);
                }
        }
    }

    protected onFilterInput(event: Event): void {
        this.filterText.set((event.target as HTMLInputElement).value);
        // The previous active option may have been filtered away, so start again at the top of what is left.
        this.keyManager.setActiveItem(this.visibleOptions().length > 0 ? 0 : -1);
    }

    /**
     * Keys typed in the search field. Everything that moves or commits the selection is forwarded to the
     * same handling the trigger uses; the rest is left to the input.
     */
    protected onFilterKeydown(event: KeyboardEvent): void {
        const navigationKeys = ['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter', 'Escape', 'Tab'];
        if (!navigationKeys.includes(event.key)) {
            return;
        }
        if (event.key === 'Home' || event.key === 'End') {
            // Home and End belong to the text field while the user is editing the query.
            return;
        }
        this.onTriggerKeydown(event);
    }

    private handleTypeahead(event: KeyboardEvent): void {
        const character = event.key.toLocaleLowerCase();
        const repeatsSequence = this.typeaheadSequence.length > 0 && [...this.typeaheadSequence].every((value) => value === character);

        clearTimeout(this.typeaheadReset);
        if (repeatsSequence) {
            this.keyManager.cancelTypeahead();
            const options = this.keyManagerOptions();
            const start = Math.max(this.activeIndex(), -1);
            const nextMatch = options.findIndex((_, offset) => options[(start + offset + 1) % options.length]?.getLabel().toLocaleLowerCase().startsWith(character));
            if (nextMatch >= 0) {
                this.keyManager.setActiveItem((start + nextMatch + 1) % options.length);
            }
            this.typeaheadSequence = character;
        } else {
            this.typeaheadSequence += character;
            this.keyManager.onKeydown(event);
        }
        this.typeaheadReset = setTimeout(() => {
            this.typeaheadSequence = '';
        }, TYPEAHEAD_DEBOUNCE_MS);
    }

    private resetTypeahead(): void {
        this.keyManager.cancelTypeahead();
        clearTimeout(this.typeaheadReset);
        this.typeaheadSequence = '';
    }

    private scrollOptionIntoView(index: number): void {
        this.document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }

    private buildTriggerClasses(): string {
        const base = 'tum-ui-select-trigger tum:box-border tum:flex tum:w-full tum:items-center tum:border tum:text-start tum:transition-colors';
        const size = TRIGGER_SIZE[this.size() ?? 'default'];
        let state: string;
        if (this.isDisabled()) {
            state = 'tum:cursor-default tum:bg-disabled-background tum:text-disabled tum:border-control-border';
        } else if (this.isInvalid()) {
            state = `tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-state-danger`;
        } else if (this.isOpen()) {
            state = 'tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-primary';
        } else {
            state = 'tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-control-border tum:hover:border-control-border-hover';
        }
        return `${base} ${size} ${state}`;
    }
    protected optionClasses(option: unknown, index: number): string {
        const base = 'tum-ui-select-option tum:flex tum:cursor-pointer tum:items-center tum:px-3 tum:py-2';
        const active = this.activeIndex() === index;
        if (this.isSelected(option)) {
            const background = active ? 'tum:bg-highlight-focus-background' : 'tum:bg-highlight-background';
            return `${base} tum:text-highlight ${background}`;
        }
        const activeState = active ? ' tum:bg-highlight-focus-background tum:text-highlight' : '';
        return `${base} tum:text-text tum:hover:bg-hover-background tum:hover:text-text-hover${activeState}`;
    }
}
