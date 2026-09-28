import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Layout container for the panels in a tabs composition. */
@Component({
    selector: 'tumaet-ui-tab-panels',
    template: '<ng-content />',
    styleUrl: './tumaet-ui-tab-panels.component.scss',
    host: {
        class: 'tumaet-ui-tab-panels',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTabPanelsComponent {}
