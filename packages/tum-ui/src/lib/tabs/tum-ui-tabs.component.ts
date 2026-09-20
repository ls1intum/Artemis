import { ChangeDetectionStrategy, Component, inject, model } from '@angular/core';
import { TumUiTabsService } from './tum-ui-tabs.service';

/** Coordinates an accessible tab list with its associated tab panels. */
@Component({
    selector: 'tum-ui-tabs',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-tabs tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:flex-col',
    },
    providers: [TumUiTabsService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabsComponent {
    private readonly tabsService = inject(TumUiTabsService);

    /** Value shared by the active tab and tab panel. */
    readonly value = model<number | string>();

    constructor() {
        this.tabsService.register(this.value, (selected) => this.value.set(selected));
    }
}
