import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Wrapper around the tab panels, part of the tum-aet-ui kit. Drop-in replacement for PrimeNG's
 * `p-tabpanels`. It only supplies the Aura panel padding around the projected `<tum-ui-tab-panel>`s;
 * which panel is visible is decided by each panel itself via the shared active value.
 */
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
