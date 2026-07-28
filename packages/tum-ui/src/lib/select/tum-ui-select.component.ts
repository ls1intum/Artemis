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
import { TumUiOverlayService } from '../overlay/tum-ui-overlay.service';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

export type TumUiSelectSize = 'small' | 'large';
const TRIGGER_SIZE: Record<'small' | 'default' | 'large', string> = {
    small: 'min-h-8 py-1.5 pl-2.5 pr-10 text-sm',
    default: 'min-h-10 py-2 pl-3 pr-10 text-base',
    large: 'min-h-12 py-2.5 pl-3.5 pr-10 text-lg',
};

let nextSelectId = 0;

@Component({
    selector: 'tum-ui-select',
    templateUrl: './tum-ui-select.component.html',
    styleUrl: './tum-ui-select.component.scss',
    imports: [A11yModule, FaIconComponent, TumUiTranslatePipe],
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

    readonly options = input<readonly unknown[]>([]);

    readonly optionLabel = input<string>();

    readonly optionValue = input<string>();

    readonly placeholder = input<string>();

    readonly disabled = input(false);

    readonly showClear = input(false);

    readonly size = input<TumUiSelectSize>();

    readonly styleClass = input<string>('');

    readonly inputId = input(`tum-ui-select-${nextSelectId++}`);
    readonly name = input<string>();
    readonly ariaLabel = input<string>();
    readonly clearAriaLabel = input<string>();
    readonly emptyMessage = input<string>();
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
        effect(() => {
            if (this.isDisabled()) {
                this.close();
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
        const selectedIndex = this.options().findIndex((option) => this.isSelected(option));
        this.activeIndex.set(selectedIndex >= 0 ? selectedIndex : this.options().length > 0 ? 0 : -1);
        const origin = this.trigger();
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom', { hasBackdrop: true });
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
        this.onTouchedCallback();
    }

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
        document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }

    protected onTriggerKeydown(event: KeyboardEvent): void {
        if (this.isDisabled()) {
            return;
        }
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

    private buildTriggerClasses(): string {
        const base = 'tum-ui-select-trigger flex w-full items-center border text-left transition-colors';
        const size = TRIGGER_SIZE[this.size() ?? 'default'];
        let state: string;
        if (this.isDisabled()) {
            state =
                'cursor-default bg-tum-ui-surface-200 text-tum-ui-surface-500 border-tum-ui-surface-300 dark:bg-tum-ui-surface-700 dark:text-tum-ui-surface-400 dark:border-tum-ui-surface-600';
        } else if (this.isOpen()) {
            state = 'cursor-pointer bg-tum-ui-surface-0 text-tum-ui-surface-700 border-tum-ui-primary dark:bg-tum-ui-surface-950 dark:text-tum-ui-surface-0';
        } else {
            state =
                'cursor-pointer bg-tum-ui-surface-0 text-tum-ui-surface-700 border-tum-ui-surface-300 hover:border-tum-ui-surface-400 dark:bg-tum-ui-surface-950 dark:text-tum-ui-surface-0 dark:border-tum-ui-surface-600 dark:hover:border-tum-ui-surface-500';
        }
        return `${base} ${size} ${state}`;
    }
    protected optionClasses(option: unknown, index: number): string {
        const base = 'tum-ui-select-option flex cursor-pointer items-center px-3 py-2';
        const active = this.activeIndex() === index;
        if (this.isSelected(option)) {
            return `${base} tum-ui-select-option-selected${active ? ' is-active' : ''}`;
        }
        const activeState = active ? ' bg-tum-ui-surface-100 text-tum-ui-surface-800 dark:bg-tum-ui-surface-800 dark:text-tum-ui-surface-0' : '';
        return `${base} text-tum-ui-surface-700 hover:bg-tum-ui-surface-100 hover:text-tum-ui-surface-800 dark:text-tum-ui-surface-0 dark:hover:bg-tum-ui-surface-800${activeState}`;
    }
}
