import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, input } from '@angular/core';
import { TabContent, TabPanel } from '@angular/aria/tabs';
import { TumUiTabsService, tabKey } from './tum-ui-tabs.service';

/**
 * Content panel shown when its value matches the containing tabs value.
 *
 * The panel element inside is an Angular Aria tab panel: it owns the `tabpanel` role and `aria-labelledby`, and renders
 * the projected content only while its tab is selected. A panel whose value matches no tab stays hidden unless its value
 * is the active one, as before; aria on its own would show it, because it only hides a panel whose tab is unselected.
 */
@Component({
    selector: 'tum-ui-tab-panel',
    imports: [TabPanel, TabContent],
    template: `
        <div
            ngTabPanel
            #panel="ngTabPanel"
            class="tum-ui-tab-panel-content tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus"
            [value]="key()"
            [hidden]="!panel.visible() || !active()"
        >
            <ng-template ngTabContent>
                @if (active()) {
                    <ng-content />
                }
            </ng-template>
        </div>
    `,
    host: {
        class: 'tum-ui-tab-panel tum:block',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabPanelComponent implements OnInit, OnDestroy {
    private readonly tabsService = inject(TumUiTabsService);
    private removeFromTabs?: () => void;

    /** Value that associates this panel with a tab. */
    readonly value = input.required<number | string>();

    protected readonly key = computed(() => tabKey(this.value()));
    protected readonly active = computed(() => this.tabsService.active() === this.value());

    ngOnInit(): void {
        this.removeFromTabs = this.tabsService.addPanel({ key: this.key });
    }

    ngOnDestroy(): void {
        this.removeFromTabs?.();
    }
}
