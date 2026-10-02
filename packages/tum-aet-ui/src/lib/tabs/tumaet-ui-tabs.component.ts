import { ChangeDetectionStrategy, Component, inject, input, model } from '@angular/core';
import { TabContent, TabPanel, Tabs } from '@angular/aria/tabs';
import { TumAetUiTabsSelectedColor, TumAetUiTabsService, TumAetUiTabsSize, TumAetUiTabsSurface } from './tumaet-ui-tabs.service';

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
    /** `small` tabs use the small font size and tighter padding, to fit a compact header such as the title row of a panel. */
    readonly size = input<TumAetUiTabsSize>('medium');
    /** Whether the selected tab's label is in the `accent` color or the `text` color; the indicator below marks it either way. */
    readonly selectedColor = input<TumAetUiTabsSelectedColor>('accent');
    /** The surface the tab list sits on: the `content` background or the `muted` one, which its scroll buttons blend into. */
    readonly surface = input<TumAetUiTabsSurface>('content');

    constructor() {
        this.tabsService.register(this.value, (selected) => this.value.set(selected));
        this.tabsService.registerAppearance({ size: this.size, selectedColor: this.selectedColor, surface: this.surface });
    }
}
