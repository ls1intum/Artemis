import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Layout container for the panels in a tabs composition. */
@Component({
    selector: 'tum-ui-tab-panels',
    template: '<ng-content />',
    styleUrl: './tum-ui-tab-panels.component.scss',
    host: {
        '[attr.data-slot]': '"tab-panels"',
        class: 'tum-ui-tab-panels',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabPanelsComponent {}
