import { ChangeDetectionStrategy, Component, ElementRef, booleanAttribute, computed, inject, input } from '@angular/core';
import type { FocusOrigin } from '@angular/cdk/a11y';
import { TumUiTabsService } from './tum-ui-tabs.service';

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

    readonly value = input.required<number | string>();
    // eslint-disable-next-line @angular-eslint/no-input-rename -- FocusKeyManager requires disabled to be a boolean property.
    readonly disabledInput = input(false, { alias: 'disabled', transform: booleanAttribute });

    get disabled(): boolean {
        return this.disabledInput();
    }

    protected readonly active = computed(() => this.tabsService.active() === this.value());
    protected readonly id = computed(() => this.tabsService.tabId(this.value()));
    protected readonly panelId = computed(() => this.tabsService.panelId(this.value()));

    protected readonly hostClasses = computed(() => {
        const state = this.active() ? 'tum:text-accent tum:border-b-primary' : 'tum:border-b-border tum:text-muted tum:hover:text-text';
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
    }
}
