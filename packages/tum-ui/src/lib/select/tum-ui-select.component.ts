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
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faChevronDown, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiOverlayService } from '../overlay/tum-ui-overlay.service';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

export type TumUiSelectSize = 'small' | 'large';
const TRIGGER_SIZE: Record<'small' | 'default' | 'large', string> = {
    small: 'tum:min-h-8 tum:py-1.5 tum:pl-2.5 tum:pr-10 tum:text-sm',
    default: 'tum:min-h-10 tum:py-2 tum:pl-3 tum:pr-10 tum:text-base',
    large: 'tum:min-h-12 tum:py-2.5 tum:pl-3.5 tum:pr-10 tum:text-lg',
};

let nextSelectId = 0;

@Component({
    selector: 'tum-ui-select',
    templateUrl: './tum-ui-select.component.html',
    styleUrl: './tum-ui-select.component.scss',
    imports: [FaIconComponent, TumUiTranslatePipe],
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
        this.overlayRef.overlayElement.querySelector<HTMLElement>('[role="listbox"]')?.focus();
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
        if (!this.isDisabled()) {
            this.trigger().nativeElement.focus();
        }
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
        const base = 'tum-ui-select-trigger tum:flex tum:w-full tum:items-center tum:border tum:text-left tum:transition-colors';
        const size = TRIGGER_SIZE[this.size() ?? 'default'];
        let state: string;
        if (this.isDisabled()) {
            state = 'tum:cursor-default tum:bg-tum-ui-disabled-background tum:text-tum-ui-disabled tum:border-tum-ui-control-border';
        } else if (this.isOpen()) {
            state = 'tum:cursor-pointer tum:bg-tum-ui-control-background tum:text-tum-ui-text tum:border-tum-ui-primary';
        } else {
            state = 'tum:cursor-pointer tum:bg-tum-ui-control-background tum:text-tum-ui-text tum:border-tum-ui-control-border tum:hover:border-tum-ui-control-border-hover';
        }
        return `${base} ${size} ${state}`;
    }
    protected optionClasses(option: unknown, index: number): string {
        const base = 'tum-ui-select-option tum:flex tum:cursor-pointer tum:items-center tum:px-3 tum:py-2';
        const active = this.activeIndex() === index;
        if (this.isSelected(option)) {
            const background = active ? 'tum:bg-tum-ui-highlight-focus-background' : 'tum:bg-tum-ui-highlight-background';
            return `${base} tum:text-tum-ui-highlight ${background}`;
        }
        const activeState = active ? ' tum:bg-tum-ui-highlight-focus-background tum:text-tum-ui-highlight' : '';
        return `${base} tum:text-tum-ui-text tum:hover:bg-tum-ui-hover-background tum:hover:text-tum-ui-text-hover${activeState}`;
    }
}
