import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TumUiTabsService } from './tum-ui-tabs.service';

@Component({
    selector: 'tum-ui-tab-panel',
    template: '@if (active()) { <ng-content /> }',
    host: {
        role: 'tabpanel',
        class: 'tum-ui-tab-panel tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-primary',
        '[id]': 'id()',
        '[attr.aria-labelledby]': 'tabId()',
        '[attr.tabindex]': 'active() ? 0 : undefined',
        '[hidden]': '!active()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabPanelComponent {
    private readonly tabsService = inject(TumUiTabsService);

    readonly value = input.required<number | string>();

    protected readonly active = computed(() => this.tabsService.active() === this.value());
    protected readonly id = computed(() => this.tabsService.panelId(this.value()));
    protected readonly tabId = computed(() => this.tabsService.tabId(this.value()));
}
