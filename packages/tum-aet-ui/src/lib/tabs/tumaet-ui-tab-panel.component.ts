import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, booleanAttribute, computed, inject, input } from '@angular/core';
import { TabContent, TabPanel } from '@angular/aria/tabs';
import { TumAetUiTabsService, tabKey } from './tumaet-ui-tabs.service';

/**
 * Content panel shown when its value matches the containing tabs value.
 *
 * The panel element inside is an Angular Aria tab panel: it owns the `tabpanel` role and `aria-labelledby`, and renders
 * the projected content only while its tab is selected. A panel whose value matches no tab stays hidden unless its value
 * is the active one, as before; aria on its own would show it, because it only hides a panel whose tab is unselected.
 */
@Component({
    selector: 'tumaet-ui-tab-panel',
    imports: [TabPanel, TabContent],
    template: `
        <div
            ngTabPanel
            #panel="ngTabPanel"
            class="tumaet-ui-tab-panel-content tumaet:focus-visible:outline tumaet:focus-visible:outline-2 tumaet:focus-visible:outline-focus"
            [value]="key()"
            [preserveContent]="preserveContent()"
            [hidden]="!panel.visible() || !active()"
        >
            <ng-template ngTabContent>
                @if (active() || preserveContent()) {
                    <ng-content />
                }
            </ng-template>
        </div>
    `,
    host: {
        class: 'tumaet-ui-tab-panel tumaet:block',
        '[hidden]': '!active()',
        '[attr.inert]': 'active() ? null : ""',
        '[attr.data-state]': "active() ? 'active' : 'inactive'",
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTabPanelComponent implements OnInit, OnDestroy {
    private readonly tabsService = inject(TumAetUiTabsService);
    private removeFromTabs?: () => void;

    /** Value that associates this panel with a tab. */
    readonly value = input.required<number | string>();
    /** Keep inactive content in the DOM. */
    readonly preserveContent = input(false, { transform: booleanAttribute });

    protected readonly key = computed(() => tabKey(this.value()));
    protected readonly active = computed(() => this.tabsService.active() === this.value());

    ngOnInit(): void {
        this.removeFromTabs = this.tabsService.addPanel({ key: this.key });
    }

    ngOnDestroy(): void {
        this.removeFromTabs?.();
    }
}
