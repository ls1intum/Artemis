import { ChangeDetectionStrategy, Component, inject, model } from '@angular/core';
import { TumUiTabsService } from './tum-ui-tabs.service';

@Component({
    selector: 'tum-ui-tabs',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-tabs flex flex-col',
    },
    providers: [TumUiTabsService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabsComponent {
    private readonly tabsService = inject(TumUiTabsService);

    readonly value = model<number | string>();

    constructor() {
        this.tabsService.register(this.value, (selected) => this.value.set(selected));
    }
}
