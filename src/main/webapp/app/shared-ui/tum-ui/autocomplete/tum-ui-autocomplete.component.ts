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
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { TumUiOverlayService } from 'app/shared-ui/tum-ui/overlay/tum-ui-overlay.service';
import { TumUiChipComponent } from 'app/shared-ui/tum-ui/chip/tum-ui-chip.component';

/** Emitted when the debounced typed query is ready — mirrors PrimeNG's `AutoCompleteCompleteEvent`. */
export interface TumUiAutoCompleteCompleteEvent {
    originalEvent?: Event;
    query: string;
}
/** Emitted when a suggestion is chosen — mirrors PrimeNG's `AutoCompleteSelectEvent`. */
export interface TumUiAutoCompleteSelectEvent {
    originalEvent?: Event;
    value: unknown;
}
/** Emitted when a selected value (chip) is removed — mirrors PrimeNG's `AutoCompleteUnselectEvent`. */
export interface TumUiAutoCompleteUnselectEvent {
    originalEvent?: Event;
    value: unknown;
}

// Per-instance counter for unique listbox / option ids so several autocompletes on one page never collide on
// aria-controls / aria-activedescendant references.
let nextAutoCompleteId = 0;

/**
 * Owned autocomplete / combobox on Angular CDK overlay, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-autocomplete`, built for the multi-select-with-chips shape the admin user
 * form uses (organization / group picker): a `role="combobox"` text input inside a bordered field, the selected
 * values rendered as removable {@link TumUiChipComponent}s in front of it, and a CDK-overlay `role="listbox"` of
 * suggestions. Styled from the exact Aura `autocomplete` tokens (multi-container border / radius / focus, overlay
 * shadow / radius, option hover / selected) so it renders like the widget it replaces, dark-mode-correct for free.
 * No PrimeNG / Bootstrap dependency; rides the shared {@link TumUiOverlayService}.
 *
 * The parent drives suggestions asynchronously exactly like `p-autocomplete`: typing (debounced by `delay`, gated
 * by `minLength`) emits {@link completeMethod} with `{ query }`; the parent filters and pushes the result into
 * `[suggestions]`. Choosing a suggestion appends a chip, writes the value through the {@link ControlValueAccessor},
 * and emits {@link onSelect} with `{ value }`; removing a chip (remove button, or Backspace on an empty input)
 * emits {@link onUnselect} with `{ value }`. Event payload shapes match PrimeNG so the admin handlers migrate with
 * only an import swap. Works unchanged with `[(ngModel)]` and reactive `formControlName`.
 */
@Component({
    selector: 'tum-ui-autocomplete',
    templateUrl: './tum-ui-autocomplete.component.html',
    styleUrl: './tum-ui-autocomplete.component.scss',
    imports: [A11yModule, TumUiChipComponent],
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

    /** The async result list the parent fills in response to {@link completeMethod}. Objects or bare primitives. */
    readonly suggestions = input<readonly unknown[]>([]);
    /** Property name to read each option's display label from. Omit for primitive options (the option itself is shown). */
    readonly optionLabel = input<string>();
    /** `p-autocomplete` alias for {@link optionLabel}; `optionLabel` wins when both are set. */
    readonly field = input<string>();
    /** Multi-select mode: renders selected values as chips and binds an array (the admin group / organization picker). */
    readonly multiple = input(false);
    /** Placeholder shown in the text input when it is empty (and, in multiple mode, no chip is selected). */
    readonly placeholder = input<string>();
    /** Disables the control (merged with a reactive-forms `setDisabledState`). */
    readonly disabled = input(false);
    /** Minimum number of typed characters before {@link completeMethod} fires (parity with `p-autocomplete [minLength]`). */
    readonly minLength = input(1);
    /** Debounce in milliseconds before {@link completeMethod} fires (parity with `p-autocomplete [delay]`). */
    readonly delay = input(300);
    /** `id` of the text input (the target of an external `<label for>`). Defaults to a unique per-instance id. */
    readonly inputId = input(`tum-ui-autocomplete-${nextAutoCompleteId++}`);
    /** Forwarded onto the input for template-driven-form parity; the CVA itself does not need it. */
    readonly name = input<string>();
    /** Accessible name for the input, forwarded as `aria-label` (use when there is no visible `<label>`). */
    readonly ariaLabel = input<string>();
    /** Accessible name for each chip's remove button; overridable for i18n. */
    readonly removeAriaLabel = input<string>('Remove');
    /** Extra classes forwarded onto the host (drop-in for `p-autocomplete styleClass`, e.g. `w-full`). */
    readonly styleClass = input<string>('');
    /** Text shown in the panel when a search returned no suggestions. */
    readonly emptyMessage = input<string>('No results found');

    /** Emits the debounced typed query so the parent can fetch + set `[suggestions]`. Parity with `(completeMethod)`. */
    readonly completeMethod = output<TumUiAutoCompleteCompleteEvent>();
    /** Emits the chosen suggestion. Parity with `(onSelect)` — `{ value }` matches the admin handler. */
    readonly onSelect = output<TumUiAutoCompleteSelectEvent>();
    /** Emits the removed value. Parity with `(onUnselect)` — `{ value }` matches the admin handler. */
    readonly onUnselect = output<TumUiAutoCompleteUnselectEvent>();

    protected readonly listboxId = `tum-ui-autocomplete-listbox-${nextAutoCompleteId++}`;

    private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
    // NOT `viewChild.required`: a standalone `[(ngModel)]` (no <form>/name) writes its value synchronously inside
    // ngOnChanges during the creation pass — before this view exists — so writeValue() → syncSingleInputText()
    // reads this query pre-view-init. A required query throws NG0951 on that read; an optional one lets the `?.`
    // guards no-op (the real value re-syncs in the later microtask, post view-init).
    private readonly textInput = viewChild<ElementRef<HTMLInputElement>>('textInput');
    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;

    protected readonly selectedValues = signal<unknown[]>([]);
    private readonly singleValue = signal<unknown>(undefined);
    protected readonly query = signal('');
    protected readonly isFocused = signal(false);
    /** True once a debounced search has fired for the current query; gates the panel so it never flashes empty on focus. */
    private readonly hasSearched = signal(false);
    protected readonly activeIndex = signal(-1);
    private readonly disabledByForm = signal(false);

    private debounceTimer?: ReturnType<typeof setTimeout>;
    private onChangeCallback: (value: unknown) => void = () => {};
    private onTouchedCallback: () => void = () => {};

    protected readonly isDisabled = computed(() => this.disabled() || this.disabledByForm());
    private readonly labelKey = computed(() => this.optionLabel() ?? this.field());

    /** The panel is shown only while focused, once a search has fired for a query that still meets `minLength`. */
    protected readonly panelVisible = computed(() => this.isFocused() && this.hasSearched() && this.query().length >= this.minLength() && !this.isDisabled());
    protected readonly activeOptionId = computed(() => (this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : undefined));
    /** Placeholder is suppressed once a chip is selected in multiple mode, matching `p-autocomplete`. */
    protected readonly inputPlaceholder = computed(() => (this.multiple() && this.selectedValues().length > 0 ? undefined : this.placeholder()));

    constructor() {
        this.destroyRef.onDestroy(() => {
            this.overlayRef?.dispose();
            if (this.debounceTimer) {
                clearTimeout(this.debounceTimer);
            }
        });
        // Open / close the overlay purely as a function of panelVisible; the panel content itself tracks
        // suggestions() reactively, so a suggestions change updates the open panel without re-creating it.
        effect(() => {
            if (this.panelVisible()) {
                this.openPanel();
            } else {
                this.closePanel();
            }
        });
        // Single-mode input text mirrors the selected value's label. Driven by an effect (not just the imperative
        // writeValue call) because a standalone `[(ngModel)]` writes the initial value before this view exists; the
        // effect re-runs once the `textInput` view-query resolves, so an initial value displays post-view-init.
        // syncSingleInputText no-ops in multiple mode and while typing (neither singleValue nor textInput changes).
        effect(() => this.syncSingleInputText());
    }

    // ---- ControlValueAccessor ---------------------------------------------------------------------

    writeValue(value: unknown): void {
        if (this.multiple()) {
            this.selectedValues.set(Array.isArray(value) ? [...value] : value == undefined ? [] : [value]);
        } else {
            // The effect in the constructor mirrors this into the input text (also post-view-init for an initial
            // value written before the view existed), so no imperative sync is needed here.
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

    // ---- Label / value helpers --------------------------------------------------------------------

    /** Display text for a value: its `optionLabel` / `field` property, or the primitive itself. */
    protected valueLabel(value: unknown): string {
        const key = this.labelKey();
        const raw = key && value !== null && typeof value === 'object' ? (value as Record<string, unknown>)[key] : value;
        return this.toText(raw);
    }

    // Values are typed `unknown`; stringify only true primitives. Objects / symbols / null have no displayable
    // text — avoids the `[object Object]` no-base-to-string trap (same guard as tum-ui-select).
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

    // Object.is folds NaN; === covers primitive equality and object reference identity.
    private valuesMatch(a: unknown, b: unknown): boolean {
        return Object.is(a, b) || a === b;
    }

    protected isAlreadySelected(option: unknown): boolean {
        return this.selectedValues().some((value) => this.valuesMatch(value, option));
    }

    protected optionId(index: number): string {
        return `${this.listboxId}-option-${index}`;
    }

    // ---- Input / typing ---------------------------------------------------------------------------

    protected focusInput(): void {
        if (!this.isDisabled()) {
            this.textInput()?.nativeElement.focus();
        }
    }

    protected onFocus(): void {
        this.isFocused.set(true);
    }

    protected onBlur(): void {
        this.isFocused.set(false);
        this.onTouchedCallback();
    }

    protected onInput(event: Event): void {
        const value = (event.target as HTMLInputElement).value;
        this.query.set(value);
        this.activeIndex.set(-1);
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }
        if (value.length >= this.minLength()) {
            this.debounceTimer = setTimeout(() => {
                this.completeMethod.emit({ originalEvent: event, query: value });
                this.hasSearched.set(true);
            }, this.delay());
        } else {
            this.hasSearched.set(false);
        }
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
                // Only intercept Enter when a suggestion is highlighted; otherwise let it bubble so a parent
                // `(keydown.enter)` (the admin's "add typed group") still fires, and prevent the surrounding
                // form from submitting.
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
                // Remove the last chip when the text field is empty (parity with p-autocomplete multiple mode).
                if (this.multiple() && this.query().length === 0 && this.selectedValues().length > 0) {
                    this.removeAt(this.selectedValues().length - 1, event);
                }
                break;
        }
    }

    protected setActive(index: number): void {
        this.activeIndex.set(index);
        // Keep the active option in view (the panel is portaled to the overlay container). Optional-call: jsdom
        // does not implement scrollIntoView and it is a no-op refinement anyway.
        document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }

    // ---- Selection --------------------------------------------------------------------------------

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
        // In single mode the input text is the selected label; only wipe it in multiple mode.
        if (el && this.multiple()) {
            el.value = '';
        }
        this.query.set('');
        this.hasSearched.set(false);
        this.activeIndex.set(-1);
    }

    /** Single mode shows the selected value's label as the input text; keep the uncontrolled input in sync. */
    private syncSingleInputText(): void {
        if (this.multiple()) {
            return;
        }
        const el = this.textInput()?.nativeElement;
        if (el) {
            const value = this.singleValue();
            el.value = value == undefined ? '' : this.valueLabel(value);
        }
    }

    // ---- Overlay ----------------------------------------------------------------------------------

    private openPanel(): void {
        if (this.overlayRef) {
            return;
        }
        const origin = this.container();
        // No backdrop: a backdrop would steal focus / pointer from the text input while the user keeps typing.
        // Outside clicks close the panel via the input's blur (→ panelVisible false).
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom');
        // Match the panel width to the field (Aura overlay `min-width: 100%`); CDK otherwise sizes to content.
        this.overlayRef.updateSize({ minWidth: origin.nativeElement.getBoundingClientRect().width });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
    }

    private closePanel(): void {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
    }

    // ---- Option styling ---------------------------------------------------------------------------

    /** Full class string for one option row (base layout + Aura hover / active / selected state colors). */
    protected optionClasses(option: unknown, index: number): string {
        const base = 'tum-ui-autocomplete-option flex cursor-pointer items-center px-3 py-2';
        const active = this.activeIndex() === index;
        if (this.isAlreadySelected(option)) {
            return `${base} tum-ui-autocomplete-option-selected${active ? ' is-active' : ''}`;
        }
        const activeState = active ? ' bg-surface-100 text-surface-800 dark:bg-surface-800 dark:text-surface-0' : '';
        return `${base} text-surface-700 hover:bg-surface-100 hover:text-surface-800 dark:text-surface-0 dark:hover:bg-surface-800${activeState}`;
    }

    /** Full class string for the multi-container field (base layout + Aura border / focus / disabled state). */
    protected containerClasses(): string {
        // Aura input-multiple: padding calc(paddingY/2)=0.25rem block + paddingX=0.75rem inline; the inline
        // padding shrinks to 0.25rem once chips are present; gap calc(paddingY/2)=0.25rem.
        const padding = this.multiple() && this.selectedValues().length > 0 ? 'p-1' : 'py-1 px-3';
        const base = `tum-ui-autocomplete-container flex w-full cursor-text flex-wrap items-center gap-1 rounded-md border text-base transition-colors ${padding}`;
        let state: string;
        if (this.isDisabled()) {
            state = 'bg-surface-200 text-surface-500 border-surface-300 dark:bg-surface-700 dark:text-surface-400 dark:border-surface-600';
        } else if (this.isFocused()) {
            state = 'bg-surface-0 text-surface-700 border-primary dark:bg-surface-950 dark:text-surface-0';
        } else {
            state =
                'bg-surface-0 text-surface-700 border-surface-300 hover:border-surface-400 dark:bg-surface-950 dark:text-surface-0 dark:border-surface-600 dark:hover:border-surface-500';
        }
        return `${base} ${state}`;
    }
}
