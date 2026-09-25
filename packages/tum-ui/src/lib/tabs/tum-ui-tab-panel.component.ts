import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, booleanAttribute, computed, inject, input } from '@angular/core';
import { TabContent, TabPanel } from '@angular/aria/tabs';
import { TumUiTabsService, tabKey } from './tum-ui-tabs.service';

/**
 * Content panel shown when its value matches the containing tabs value.
 *
 * The panel element inside is an Angular Aria tab panel: it owns the `tabpanel` role and `aria-labelledby`, and renders
 * the projected content only while its tab is selected. Set `preserveContent` to keep the content while another tab is
 * selected; the inactive panel then stays hidden and inert.
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
        class: 'tum-ui-tab-panel tum:block',
        '[hidden]': '!active()',
        '[attr.inert]': 'active() ? null : ""',
        '[attr.data-state]': "active() ? 'active' : 'inactive'",
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabPanelComponent implements OnInit, OnDestroy {
    private readonly tabsService = inject(TumUiTabsService);
    private removeFromTabs?: () => void;

    /** Value that associates this panel with a tab. */
    readonly value = input.required<number | string>();

    /** Keeps inactive panel content in the DOM. */
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
