import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tum-ui-tab-panels',
    template: '<ng-content />',
    styleUrl: './tum-ui-tab-panels.component.scss',
    host: {
        class: 'tum-ui-tab-panels',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabPanelsComponent {}
