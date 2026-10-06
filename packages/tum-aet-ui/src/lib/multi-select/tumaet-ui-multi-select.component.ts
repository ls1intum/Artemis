import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    Injector,
    TemplateRef,
    ViewContainerRef,
    booleanAttribute,
    computed,
    contentChild,
    effect,
    forwardRef,
    inject,
    input,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import { OverlayRef } from '@angular/cdk/overlay';
import { ListKeyManager } from '@angular/cdk/a11y';
import { TemplatePortal } from '@angular/cdk/portal';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiOverlayService } from '../overlay/tumaet-ui-overlay.service';
import { TumAetUiTranslatePipe } from '../i18n/tumaet-ui-translate.pipe';
import { TUM_AET_UI_FORM_FIELD } from '../form-field/tumaet-ui-form-field.token';

export type TumAetUiMultiSelectSize = 'small' | 'large';

/** `outlined` matches an outlined primary button, for a control that sits in a toolbar beside such buttons. */
export type TumAetUiMultiSelectVariant = 'default' | 'outlined';

const TRIGGER_SIZE: Record<'small' | 'default' | 'large', string> = {
    small: 'tumaet:min-h-8 tumaet:py-1 tumaet:ps-2.5 tumaet:text-sm',
    default: 'tumaet:min-h-10 tumaet:py-1.5 tumaet:ps-3 tumaet:text-base',
    large: 'tumaet:min-h-12 tumaet:py-2 tumaet:ps-3.5 tumaet:text-lg',
};

/** The panel is at least as wide as the trigger and never narrower than this, so that long option labels stay readable. */
const MIN_PANEL_WIDTH_PX = 192;

let nextMultiSelectId = 0;

/**
 * ControlValueAccessor that chooses any number of values from a listbox overlay. The value is an array of the chosen
 * options (or of their `optionValue` property); the panel stays open while options are toggled.
 *
 * The trigger is a `div` with `role="combobox"` rather than a `button`, so that a projected `#selectedItems` template
 * may hold interactive content of its own (a chip with a remove button, say).
 *
 * Content projection slots, matched by template reference name:
 * - `<ng-template #option let-option let-selected="selected">`: replaces the label of an option row.
 * - `<ng-template #selectedItems let-options>`: replaces the summary inside the trigger; `$implicit` is the array of chosen options.
 */
@Component({
    selector: 'tumaet-ui-multi-select',
    templateUrl: './tumaet-ui-multi-select.component.html',
    styleUrl: './tumaet-ui-multi-select.component.scss',
    imports: [FaIconComponent, NgTemplateOutlet, TumAetUiTranslatePipe],
    host: {
        class: 'tumaet-ui-multi-select',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumAetUiMultiSelectComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiMultiSelectComponent implements ControlValueAccessor {
    private readonly overlayService = inject(TumAetUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);
    private readonly document = inject(DOCUMENT);
    private readonly injector = inject(Injector);
    private readonly formField = inject(TUM_AET_UI_FORM_FIELD, { optional: true });

    readonly options = input<readonly unknown[]>([]);

    /** Property name used as the visible label for object options. */
    readonly optionLabel = input<string>();

    /** Property name written to the form value; omit it to write the option itself. */
    readonly optionValue = input<string>();

    readonly placeholder = input<string>();

    readonly disabled = input(false, { transform: booleanAttribute });

    /** Reports the control as required to assistive technology. Validation stays with the form control. */
    readonly required = input(false, { transform: booleanAttribute });

    readonly size = input<TumAetUiMultiSelectSize>();

    readonly variant = input<TumAetUiMultiSelectVariant>('default');

    /**
     * How many chosen labels the default summary lists before it falls back to a count. A `#selectedItems` template
     * replaces the summary and ignores this.
     */
    readonly maxSelectedLabels = input(3);

    /** `id` of the trigger, so an external `<label for>` associates. Defaults to the id of an enclosing `tumaet-ui-form-field`. */
    readonly inputId = input<string>();
    readonly name = input<string>();
    readonly ariaLabel = input<string>();
    readonly emptyMessage = input<string>();

    /** Emits the new array of chosen values whenever an option is toggled. */
    readonly selectionChange = output<unknown[]>();

    protected readonly optionTemplate = contentChild<TemplateRef<unknown>>('option');
    protected readonly selectedItemsTemplate = contentChild<TemplateRef<unknown>>('selectedItems');

    protected readonly faChevronDown = faChevronDown;
    protected readonly faCheck = faCheck;

    private readonly fallbackInputId = `tumaet-ui-multi-select-${nextMultiSelectId++}`;
    protected readonly resolvedInputId = computed(() => this.formField?.explicitControlId() ?? this.inputId() ?? this.formField?.labelTargetId() ?? this.fallbackInputId);
    /** The label of an enclosing form field names the trigger, unless the consumer gave it an `ariaLabel` of its own. */
    protected readonly labelledBy = computed(() => (this.ariaLabel() ? null : (this.formField?.labelId() ?? null)));
    protected readonly describedBy = computed(() => this.formField?.describedBy() ?? null);
    protected readonly isInvalid = computed(() => this.formField?.invalid() ?? false);
    protected readonly listboxId = `tumaet-ui-multi-select-listbox-${nextMultiSelectId++}`;

    private readonly trigger = viewChild.required<ElementRef<HTMLElement>>('trigger');
    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;

    protected readonly isOpen = signal(false);
    protected readonly activeIndex = signal(-1);
    private readonly selectedValues = signal<readonly unknown[]>([]);
    private readonly disabledByForm = signal(false);

    private onChangeCallback: (value: unknown[]) => void = () => {};
    private onTouchedCallback: () => void = () => {};

    protected readonly isDisabled = computed(() => this.disabled() || this.disabledByForm());

    /** The chosen options in the order of the option list, so the summary does not depend on the order of choosing. */
    protected readonly selectedOptions = computed(() => this.options().filter((option) => this.isSelected(option)));
    protected readonly hasSelection = computed(() => this.selectedOptions().length > 0);
    protected readonly summaryLabels = computed(() => this.selectedOptions().map((option) => this.label(option)));
    protected readonly showCount = computed(() => this.summaryLabels().length > this.maxSelectedLabels());
    protected readonly summaryText = computed(() => this.summaryLabels().join(', '));
    protected readonly activeOptionId = computed(() => (this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : undefined));
    protected readonly triggerClasses = computed(() => this.buildTriggerClasses());
    protected readonly chevronClasses = computed(() => (this.variant() === 'outlined' && !this.isDisabled() ? 'tumaet:text-accent' : 'tumaet:text-muted'));

    private readonly keyManagerOptions = computed(() => this.options().map((option) => ({ getLabel: () => this.label(option) })));
    private readonly keyManager = new ListKeyManager(this.keyManagerOptions, this.injector).withVerticalOrientation().withHomeAndEnd();

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
    }

    writeValue(value: unknown): void {
        this.selectedValues.set(Array.isArray(value) ? value : []);
    }

    registerOnChange(fn: (value: unknown[]) => void): void {
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
        switch (typeof raw) {
            case 'string':
                return raw;
            case 'number':
            case 'boolean':
            case 'bigint':
                return String(raw);
            default:
                return '';
        }
    }

    private resolveValue(option: unknown): unknown {
        const key = this.optionValue();
        if (key && option !== null && typeof option === 'object') {
            return (option as Record<string, unknown>)[key];
        }
        return option;
    }

    protected isSelected(option: unknown): boolean {
        const value = this.resolveValue(option);
        return this.selectedValues().some((selected) => Object.is(selected, value));
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
        const firstSelected = this.options().findIndex((option) => this.isSelected(option));
        const initialIndex = firstSelected >= 0 ? firstSelected : this.options().length > 0 ? 0 : -1;
        this.keyManager.setActiveItem(initialIndex);
        const origin = this.trigger();
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom', { hasBackdrop: true });
        this.overlayRef.updateSize({ minWidth: Math.max(origin.nativeElement.getBoundingClientRect().width, MIN_PANEL_WIDTH_PX) });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.scrollOptionIntoView(initialIndex);
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.isOpen.set(true);
    }

    private close(restoreFocus = true): void {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.isOpen.set(false);
        this.onTouchedCallback();
        if (restoreFocus && !this.isDisabled()) {
            this.trigger().nativeElement.focus();
        }
    }

    /**
     * Reports the control as touched when focus leaves it for good. While the panel is open, focus staying on the
     * trigger or moving into the panel is not leaving; closing the panel reports touched itself.
     */
    protected onTriggerFocusOut(event: FocusEvent): void {
        const next = event.relatedTarget;
        const trigger = this.trigger().nativeElement;
        if (this.isOpen() || (next instanceof Node && trigger.contains(next))) {
            return;
        }
        this.onTouchedCallback();
    }

    protected toggleOption(option: unknown): void {
        const value = this.resolveValue(option);
        const next = this.isSelected(option) ? this.selectedValues().filter((selected) => !Object.is(selected, value)) : [...this.selectedValues(), value];
        this.selectedValues.set(next);
        this.onChangeCallback([...next]);
        this.selectionChange.emit([...next]);
    }

    protected setActive(index: number): void {
        this.keyManager.setActiveItem(index);
    }

    protected onTriggerKeydown(event: KeyboardEvent): void {
        if (this.isDisabled() || event.target !== event.currentTarget) {
            // Keys pressed inside a projected control (a chip's remove button) belong to that control.
            return;
        }
        if (!this.isOpen()) {
            if (['Enter', ' ', 'Spacebar', 'ArrowDown', 'ArrowUp'].includes(event.key)) {
                event.preventDefault();
                this.open();
            }
            return;
        }
        const count = this.options().length;
        switch (event.key) {
            case 'Enter':
            case ' ':
            case 'Spacebar':
                event.preventDefault();
                if (this.activeIndex() >= 0 && this.activeIndex() < count) {
                    this.toggleOption(this.options()[this.activeIndex()]);
                }
                break;
            case 'Escape':
                this.close();
                break;
            case 'Tab':
                this.close(false);
                break;
            default:
                this.keyManager.onKeydown(event);
        }
    }

    private scrollOptionIntoView(index: number): void {
        this.document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }

    private buildTriggerClasses(): string {
        const base =
            'tumaet-ui-multi-select-trigger tumaet:box-border tumaet:flex tumaet:w-full tumaet:items-center tumaet:gap-2 tumaet:border tumaet:pe-10 tumaet:text-start tumaet:transition-colors';
        const size = TRIGGER_SIZE[this.size() ?? 'default'];
        let state: string;
        if (this.isDisabled()) {
            state = 'tumaet:cursor-default tumaet:bg-disabled-background tumaet:text-disabled tumaet:border-control-border';
        } else if (this.isInvalid()) {
            state = 'tumaet:cursor-pointer tumaet:bg-control-background tumaet:text-text tumaet:border-state-danger';
        } else if (this.variant() === 'outlined') {
            state = 'tumaet:cursor-pointer tumaet:bg-transparent tumaet:text-accent tumaet:border-primary';
        } else if (this.isOpen()) {
            state = 'tumaet:cursor-pointer tumaet:bg-control-background tumaet:text-text tumaet:border-primary';
        } else {
            state = 'tumaet:cursor-pointer tumaet:bg-control-background tumaet:text-text tumaet:border-control-border tumaet:hover:border-control-border-hover';
        }
        return `${base} ${size} ${state}`;
    }

    protected optionClasses(index: number): string {
        const base = 'tumaet-ui-multi-select-option tumaet:flex tumaet:cursor-pointer tumaet:items-center tumaet:gap-2 tumaet:px-3 tumaet:py-2';
        const active = this.activeIndex() === index;
        const activeState = active ? ' tumaet:bg-highlight-focus-background tumaet:text-highlight' : '';
        return `${base} tumaet:text-text tumaet:hover:bg-hover-background tumaet:hover:text-text-hover${activeState}`;
    }

    protected boxClasses(option: unknown): string {
        return this.isSelected(option) ? 'tumaet:bg-primary tumaet:border-primary tumaet:text-primary-contrast' : 'tumaet:bg-control-background tumaet:border-control-border';
    }
}
