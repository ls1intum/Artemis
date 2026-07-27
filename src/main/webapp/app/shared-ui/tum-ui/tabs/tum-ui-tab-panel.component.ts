import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TumUiTabsService } from 'app/shared-ui/tum-ui/tabs/tum-ui-tabs.service';

/**
 * A single tab panel, part of the tum-aet-ui kit. Drop-in replacement for PrimeNG's `p-tabpanel`.
 *
 * Renders a `role="tabpanel"` whose content is projected only while its `value` equals the group's active
 * value (inactive panels are unmounted and `hidden`, matching PrimeNG's lazy behavior). Its `id` and
 * `aria-labelledby` are wired to the owning `<tum-ui-tab>` through the shared {@link TumUiTabsService}.
 */
@Component({
    selector: 'tum-ui-tab-panel',
    template: '@if (active()) { <ng-content /> }',
    host: {
        role: 'tabpanel',
        class: 'tum-ui-tab-panel focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary',
        '[id]': 'id()',
        '[attr.aria-labelledby]': 'tabId()',
        '[attr.tabindex]': 'active() ? 0 : undefined',
        '[hidden]': '!active()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabPanelComponent {
    private readonly tabsService = inject(TumUiTabsService);

    /** This panel's key; must match a `<tum-ui-tab>`'s `value`. Numbers and strings are both supported. */
    readonly value = input.required<number | string>();

    protected readonly active = computed(() => this.tabsService.active() === this.value());
    protected readonly id = computed(() => this.tabsService.panelId(this.value()));
    protected readonly tabId = computed(() => this.tabsService.tabId(this.value()));
}
