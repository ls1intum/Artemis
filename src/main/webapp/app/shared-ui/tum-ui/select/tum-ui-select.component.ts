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
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faChevronDown, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiOverlayService } from 'app/shared-ui/tum-ui/overlay/tum-ui-overlay.service';

/** Trigger padding + font per size, matching the Aura `select` sm / (default) / lg form-field tokens. */
export type TumUiSelectSize = 'small' | 'large';
const TRIGGER_SIZE: Record<'small' | 'default' | 'large', string> = {
    // Aura form.field.sm: 0.375rem / 0.625rem, font 0.875rem. `pr-10` reserves the 2.5rem dropdown zone.
    small: 'py-1.5 pl-2.5 pr-10 text-sm',
    // Aura form.field: 0.5rem / 0.75rem, font 1rem.
    default: 'py-2 pl-3 pr-10 text-base',
    // Aura form.field.lg: 0.625rem / 0.875rem, font 1.125rem.
    large: 'py-2.5 pl-3.5 pr-10 text-lg',
};

// Per-instance counter for unique listbox / option ids, so several selects on one page (the admin
// forms render many) never collide on aria-controls / aria-activedescendant references.
let nextSelectId = 0;

/**
 * Owned single-select dropdown on Angular CDK overlay, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-select`: a `role="button"` trigger + a CDK-overlay `role="listbox"`
 * panel, styled from the exact Aura `select` tokens (trigger border / height / padding, primary focus border,
 * chevron, panel shadow / radius, option hover / selected / focus states) so it renders like the widget it
 * replaces, dark-mode-correct for free. No PrimeNG / Bootstrap dependency; rides the shared
 * {@link TumUiOverlayService}.
 *
 * Unlike {@link TumUiDatePickerComponent} (Signal Forms), this is a classic {@link ControlValueAccessor} so it
 * works unchanged with BOTH `[(ngModel)]` and reactive `formControlName` — the two binding styles the admin
 * screens use. `optionValue` selects a primitive value out of each option object (e.g. `id`); omit it and the
 * whole option object becomes the value (reference-compared), matching `p-select`.
 */
@Component({
    selector: 'tum-ui-select',
    templateUrl: './tum-ui-select.component.html',
    styleUrl: './tum-ui-select.component.scss',
    imports: [A11yModule, FaIconComponent],
    host: {
        '[class]': 'styleClass()',
    },
    providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectComponent), multi: true }],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiSelectComponent implements ControlValueAccessor {
    private readonly overlayService = inject(TumUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);

    /** The selectable options. Objects (read via `optionLabel` / `optionValue`) or bare primitives. */
    readonly options = input<readonly unknown[]>([]);
    /** Property name to read each option's display label from. Omit for primitive options (the option itself is shown). */
    readonly optionLabel = input<string>();
    /** Property name to read each option's bound value from. Omit to bind the whole option object (like `p-select`). */
    readonly optionValue = input<string>();
    /** Text shown on the trigger when nothing is selected. */
    readonly placeholder = input<string>();
    /** Disables the control (merged with a reactive-forms `setDisabledState`). */
    readonly disabled = input(false);
    /** Renders a clear (×) button on the trigger once a value is selected (parity with `p-select [showClear]`). */
    readonly showClear = input(false);
    /**
     * `p-select` API-parity input. No-op here: the panel always renders in the CDK overlay container (body-level),
     * so it is never clipped by an overflow ancestor — which is exactly what `appendTo="body"` bought in PrimeNG.
     */
    readonly appendTo = input<string>();
    /** `small` / `large`, matching the Aura form-field sizes; omit for the default size. */
    readonly size = input<TumUiSelectSize>();
    /** Extra classes forwarded onto the host (drop-in for `p-select styleClass`, e.g. `w-full` / `w-auto`). */
    readonly styleClass = input<string>('');
    /** `id` of the trigger `<button>` (the target of an external `<label for>`). Defaults to a unique per-instance id. */
    readonly inputId = input(`tum-ui-select-${nextSelectId++}`);
    /** Forwarded onto the trigger for template-driven-form parity; the CVA itself does not need it. */
    readonly name = input<string>();
    /** Accessible name for the trigger, forwarded as `aria-label` (use when there is no visible `<label>`). */
    readonly ariaLabel = input<string>();
    /** Accessible name for the clear button; overridable for i18n. */
    readonly clearAriaLabel = input<string>('Clear selection');
    /** Text shown in the panel when there are no options. */
    readonly emptyMessage = input<string>('No available options');

    /**
     * Emits the newly-selected value (the resolved `optionValue`, or the whole option when `optionValue` is unset;
     * `undefined` on clear). Drop-in for `p-select (onChange)` for the no-argument handlers the admin screens use;
     * the payload is the value itself rather than PrimeNG's `{ originalEvent, value }` wrapper.
     */
    readonly onChange = output<unknown>();

    protected readonly faChevronDown = faChevronDown;
    protected readonly faCheck = faCheck;
    protected readonly faXmark = faXmark;

    protected readonly listboxId = `tum-ui-select-listbox-${nextSelectId++}`;

    private readonly trigger = viewChild.required<ElementRef<HTMLElement>>('trigger');
    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private overlayRef?: OverlayRef;

    protected readonly isOpen = signal(false);
    protected readonly activeIndex = signal(-1);
    private readonly selectedValue = signal<unknown>(undefined);
    private readonly disabledByForm = signal(false);

    private onChangeCallback: (value: unknown) => void = () => {};
    private onTouchedCallback: () => void = () => {};

    private typeaheadBuffer = '';
    private typeaheadTimer?: ReturnType<typeof setTimeout>;

    protected readonly isDisabled = computed(() => this.disabled() || this.disabledByForm());

    /** The option whose bound value equals the current model value, or undefined if none matches (→ placeholder). */
    protected readonly selectedOption = computed(() => {
        const current = this.selectedValue();
        if (current === undefined || current === null) {
            return undefined;
        }
        return this.options().find((option) => this.valuesMatch(this.resolveValue(option), current));
    });

    protected readonly hasSelection = computed(() => this.selectedOption() !== undefined);
    protected readonly displayLabel = computed(() => {
        const option = this.selectedOption();
        return option !== undefined ? this.label(option) : (this.placeholder() ?? '');
    });
    protected readonly showClearButton = computed(() => this.showClear() && this.hasSelection() && !this.isDisabled());
    protected readonly triggerClasses = computed(() => this.buildTriggerClasses());
    protected readonly activeOptionId = computed(() => (this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : undefined));

    constructor() {
        this.destroyRef.onDestroy(() => {
            this.overlayRef?.dispose();
            if (this.typeaheadTimer) {
                clearTimeout(this.typeaheadTimer);
            }
        });
        // Close the panel if the control is disabled while open (e.g. a reactive-forms disable), mirroring
        // the date picker. open() already guards on disabled; this covers the disable-while-open case.
        effect(() => {
            if (this.isDisabled()) {
                this.close();
            }
        });
    }

    // ---- ControlValueAccessor ---------------------------------------------------------------------

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

    // ---- Option/value helpers ---------------------------------------------------------------------

    /** Display text for an option: its `optionLabel` property, or the primitive itself. */
    protected label(option: unknown): string {
        const key = this.optionLabel();
        const raw = key && option !== null && typeof option === 'object' ? (option as Record<string, unknown>)[key] : option;
        return this.toText(raw);
    }

    // Options are typed `unknown`; stringify only true primitives (labels are strings/numbers). Objects,
    // symbols, null / undefined have no displayable text — avoids the `[object Object]` no-base-to-string trap.
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

    /** Bound value for an option: its `optionValue` property, or the whole option when `optionValue` is unset. */
    private resolveValue(option: unknown): unknown {
        const key = this.optionValue();
        if (key && option !== null && typeof option === 'object') {
            return (option as Record<string, unknown>)[key];
        }
        return option;
    }

    // Object.is folds NaN; === covers primitive equality and object reference identity (matching p-select's
    // reference comparison when no optionValue/dataKey is given).
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

    // ---- Open / close -----------------------------------------------------------------------------

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
        const selectedIndex = this.options().findIndex((option) => this.isSelected(option));
        this.activeIndex.set(selectedIndex >= 0 ? selectedIndex : this.options().length > 0 ? 0 : -1);
        const origin = this.trigger();
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom', { hasBackdrop: true });
        // Match the panel width to the trigger (Aura panel `min-width: 100%`); CDK otherwise sizes to content.
        this.overlayRef.updateSize({ minWidth: origin.nativeElement.getBoundingClientRect().width });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.isOpen.set(true);
    }

    private close(): void {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.isOpen.set(false);
        // Focus returns to the trigger via cdkTrapFocus auto-capture restore; mark the control touched (blur).
        this.onTouchedCallback();
    }

    // ---- Selection --------------------------------------------------------------------------------

    protected selectOption(option: unknown): void {
        const value = this.resolveValue(option);
        this.selectedValue.set(value);
        this.onChangeCallback(value);
        this.onChange.emit(value);
        this.close();
    }

    protected clear(event: MouseEvent): void {
        event.stopPropagation();
        this.selectedValue.set(undefined);
        this.onChangeCallback(undefined);
        this.onChange.emit(undefined);
        this.onTouchedCallback();
    }

    protected setActive(index: number): void {
        this.activeIndex.set(index);
        // Keep the active option in view during keyboard navigation (the panel is portaled to the overlay container).
        // Optional-call the method: jsdom does not implement scrollIntoView, and it is a no-op refinement anyway.
        document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }

    // ---- Keyboard ---------------------------------------------------------------------------------

    protected onTriggerKeydown(event: KeyboardEvent): void {
        if (this.isDisabled()) {
            return;
        }
        // Enter / Space fire a native button click → toggle(); only Arrow-open and Escape need handling here.
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            if (!this.isOpen()) {
                this.open();
            }
        } else if (event.key === 'Escape' && this.isOpen()) {
            this.close();
        }
    }

    protected onListKeydown(event: KeyboardEvent): void {
        const count = this.options().length;
        switch (event.key) {
            case 'ArrowDown':
                event.preventDefault();
                if (count > 0) {
                    this.setActive(Math.min(count - 1, this.activeIndex() + 1));
                }
                break;
            case 'ArrowUp':
                event.preventDefault();
                if (count > 0) {
                    this.setActive(Math.max(0, this.activeIndex() - 1));
                }
                break;
            case 'Home':
                event.preventDefault();
                if (count > 0) {
                    this.setActive(0);
                }
                break;
            case 'End':
                event.preventDefault();
                if (count > 0) {
                    this.setActive(count - 1);
                }
                break;
            case 'Enter':
            case ' ':
            case 'Spacebar':
                event.preventDefault();
                if (this.activeIndex() >= 0 && this.activeIndex() < count) {
                    this.selectOption(this.options()[this.activeIndex()]);
                }
                break;
            case 'Escape':
            case 'Tab':
                this.close();
                break;
            default:
                if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
                    this.typeahead(event.key);
                }
        }
    }

    private typeahead(char: string): void {
        if (this.typeaheadTimer) {
            clearTimeout(this.typeaheadTimer);
        }
        this.typeaheadBuffer += char.toLowerCase();
        const index = this.options().findIndex((option) => this.label(option).toLowerCase().startsWith(this.typeaheadBuffer));
        if (index >= 0) {
            this.setActive(index);
        }
        this.typeaheadTimer = setTimeout(() => (this.typeaheadBuffer = ''), 500);
    }

    // ---- Trigger styling --------------------------------------------------------------------------

    private buildTriggerClasses(): string {
        const base = 'tum-ui-select-trigger flex w-full items-center border text-left transition-colors';
        const size = TRIGGER_SIZE[this.size() ?? 'default'];
        let state: string;
        if (this.isDisabled()) {
            state = 'cursor-default bg-surface-200 text-surface-500 border-surface-300 dark:bg-surface-700 dark:text-surface-400 dark:border-surface-600';
        } else if (this.isOpen()) {
            state = 'cursor-pointer bg-surface-0 text-surface-700 border-primary dark:bg-surface-950 dark:text-surface-0';
        } else {
            state =
                'cursor-pointer bg-surface-0 text-surface-700 border-surface-300 hover:border-surface-400 dark:bg-surface-950 dark:text-surface-0 dark:border-surface-600 dark:hover:border-surface-500';
        }
        return `${base} ${size} ${state}`;
    }

    /** Full class string for one option row (base layout + Aura hover / active / selected state colors). */
    protected optionClasses(option: unknown, index: number): string {
        const base = 'tum-ui-select-option flex cursor-pointer items-center px-3 py-2';
        const active = this.activeIndex() === index;
        if (this.isSelected(option)) {
            return `${base} tum-ui-select-option-selected${active ? ' is-active' : ''}`;
        }
        const activeState = active ? ' bg-surface-100 text-surface-800 dark:bg-surface-800 dark:text-surface-0' : '';
        return `${base} text-surface-700 hover:bg-surface-100 hover:text-surface-800 dark:text-surface-0 dark:hover:bg-surface-800${activeState}`;
    }
}
