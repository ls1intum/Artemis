import { ChangeDetectionStrategy, Component, inject, model } from '@angular/core';
import { TabContent, TabPanel, Tabs } from '@angular/aria/tabs';
import { TumAetUiTabsService } from './tumaet-ui-tabs.service';

/**
 * Coordinates an accessible tab list with its associated tab panels.
 *
 * Built on the Angular Aria tabs: `tumaet-ui-tab-list` is the aria tab list, every `tumaet-ui-tab` an aria tab, and every
 * `tumaet-ui-tab-panel` holds an aria tab panel. The panels are optional. Without them the tab list switches a view the host
 * renders itself, bound to `value`.
 */
@Component({
    selector: 'tumaet-ui-tabs',
    imports: [TabPanel, TabContent],
    template: `
        <ng-content />
        @for (key of tabsService.keysWithoutPanel(); track key) {
            <div ngTabPanel [value]="key" hidden><ng-template ngTabContent /></div>
        }
    `,
    hostDirectives: [Tabs],
    host: {
        // Lets the Angular Aria test harnesses (`@angular/aria/tabs/testing`) find the container.
        ngTabs: '',
        class: 'tumaet-ui-tabs tumaet:flex tumaet:w-full tumaet:min-w-0 tumaet:max-w-full tumaet:flex-col',
    },
    providers: [TumAetUiTabsService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTabsComponent {
    protected readonly tabsService = inject(TumAetUiTabsService);

    /** Value shared by the active tab and tab panel. */
    readonly value = model<number | string>();

    constructor() {
        this.tabsService.register(this.value, (selected) => this.value.set(selected));
    }
}
