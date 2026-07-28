import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input } from '@angular/core';
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
        '[attr.aria-disabled]': 'disabled() || undefined',
        '[attr.tabindex]': 'active() ? 0 : -1',
        '(click)': 'onClick()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabComponent {
    private readonly tabsService = inject(TumUiTabsService);

    readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

    readonly value = input.required<number | string>();
    readonly disabled = input(false);

    protected readonly active = computed(() => this.tabsService.active() === this.value());
    protected readonly id = computed(() => this.tabsService.tabId(this.value()));
    protected readonly panelId = computed(() => this.tabsService.panelId(this.value()));

    protected readonly hostClasses = computed(() => {
        const state = this.active() ? 'tum:text-tum-ui-primary tum:border-b-tum-ui-primary' : 'tum:border-b-tum-ui-border tum:text-tum-ui-muted tum:hover:text-tum-ui-text';
        const disabled = this.disabled() ? 'tum-ui-tab-disabled' : '';
        return `tum-ui-tab tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-tum-ui-primary ${state} ${disabled}`.trim();
    });

    protected onClick(): void {
        if (!this.disabled()) {
            this.tabsService.select(this.value());
        }
    }
}
