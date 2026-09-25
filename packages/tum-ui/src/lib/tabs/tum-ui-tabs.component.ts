import { ChangeDetectionStrategy, Component, inject, model } from '@angular/core';
import { TabContent, TabPanel, Tabs } from '@angular/aria/tabs';
import { TumUiTabsService } from './tum-ui-tabs.service';

/**
 * Coordinates an accessible tab list with its associated tab panels.
 *
 * Built on the Angular Aria tabs: `tum-ui-tab-list` is the aria tab list, every `tum-ui-tab` an aria tab, and every
 * `tum-ui-tab-panel` holds an aria tab panel. The panels are optional. Without them the tab list switches a view the host
 * renders itself, bound to `value`.
 */
@Component({
    selector: 'tum-ui-tabs',
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
        class: 'tum-ui-tabs tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:flex-col',
    },
    providers: [TumUiTabsService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabsComponent {
    protected readonly tabsService = inject(TumUiTabsService);

    /** Value shared by the active tab and tab panel. */
    readonly value = model<number | string>();

    constructor() {
        this.tabsService.register(this.value, (selected) => this.value.set(selected));
    }
}
