import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, booleanAttribute, computed, effect, inject, input } from '@angular/core';
import type { FocusOrigin } from '@angular/cdk/a11y';
import { TumUiTabsService } from './tum-ui-tabs.service';

/** Selectable tab associated with the panel that has the same value. */
@Component({
    selector: 'tum-ui-tab',
    template: '<ng-content />',
    styleUrl: './tum-ui-tab.component.scss',
    host: {
        role: 'tab',
        '[class]': 'hostClasses()',
        '[id]': 'id()',
        '[attr.aria-selected]': 'active()',
        '[attr.aria-controls]': 'panelId()',
        '[attr.aria-disabled]': 'disabled || undefined',
        '[attr.tabindex]': 'active() && !disabled ? 0 : -1',
        '(click)': 'onClick()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabComponent {
    private readonly tabsService = inject(TumUiTabsService);

    readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

    /** Value that associates this tab with a tab panel. */
    readonly value = input.required<number | string>();
    // eslint-disable-next-line @angular-eslint/no-input-rename -- FocusKeyManager requires disabled to be a boolean property.
    readonly disabledInput = input(false, { alias: 'disabled', transform: booleanAttribute });

    get disabled(): boolean {
        return this.disabledInput();
    }

    constructor() {
        // Publish the value from the tab's own change detection, where the required input is always available. The tab
        // list cannot read the input directly: its content query reports a tab declared inside @if or @for before
        // Angular has applied the binding, and reading it then throws NG0950.
        effect(() => this.tabsService.publish(this, this.value()));
        inject(DestroyRef).onDestroy(() => this.tabsService.unpublish(this));
    }

    protected readonly active = computed(() => this.tabsService.active() === this.value());
    protected readonly id = computed(() => this.tabsService.tabId(this.value()));
    protected readonly panelId = computed(() => this.tabsService.panelId(this.value()));

    protected readonly hostClasses = computed(() => {
        const state = this.active() ? 'tum:text-accent' : 'tum:text-muted tum:hover:text-text';
        const disabled = this.disabled ? 'tum-ui-tab-disabled' : '';
        return `tum-ui-tab tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus ${state} ${disabled}`.trim();
    });

    protected onClick(): void {
        if (!this.disabled) {
            this.tabsService.select(this.value());
        }
    }

    focus(_origin?: FocusOrigin): void {
        this.elementRef.nativeElement.focus();
        this.elementRef.nativeElement.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
    }
}
