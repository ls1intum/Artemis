import { ChangeDetectionStrategy, Component, booleanAttribute, computed, inject, input } from '@angular/core';
import { TumUiTabsService } from './tum-ui-tabs.service';

/**
 * Content panel shown when its value matches the containing tabs value.
 *
 * By default an inactive panel is destroyed, which is the right default: a tab a user is not looking at should not
 * keep a subscription open or hold a large view alive. Turn on `preserveContent` for a panel whose state the user
 * expects to survive a trip to another tab — scroll position, an expanded row, an in-progress filter — because
 * destroying it re-runs every child constructor and returns the user to the top of a list they had scrolled.
 */
@Component({
    selector: 'tum-ui-tab-panel',
    template: '@if (rendered()) { <ng-content /> }',
    host: {
        role: 'tabpanel',
        class: 'tum-ui-tab-panel tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus',
        '[id]': 'id()',
        '[attr.aria-labelledby]': 'tabId()',
        '[attr.tabindex]': 'active() ? 0 : undefined',
        '[hidden]': '!active()',
        // `hidden` takes it out of the accessibility tree; `inert` takes it out of the tab order and out of reach
        // of find-in-page, which `hidden` alone does not guarantee for preserved content.
        '[attr.inert]': 'active() ? null : ""',
        '[attr.data-slot]': '"tab-panel"',
        '[attr.data-state]': "active() ? 'active' : 'inactive'",
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabPanelComponent {
    private readonly tabsService = inject(TumUiTabsService);

    /** Value that associates this panel with a tab. */
    readonly value = input.required<number | string>();

    /** Keeps this panel's content in the DOM while another tab is selected, hidden and inert, instead of destroying it. */
    readonly preserveContent = input(false, { transform: booleanAttribute });

    protected readonly active = computed(() => this.tabsService.active() === this.value());
    protected readonly rendered = computed(() => this.active() || this.preserveContent());
    protected readonly id = computed(() => this.tabsService.panelId(this.value()));
    protected readonly tabId = computed(() => this.tabsService.tabId(this.value()));
}
