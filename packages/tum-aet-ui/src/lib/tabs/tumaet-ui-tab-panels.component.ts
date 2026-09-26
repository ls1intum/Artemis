import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/** Layout container for the panels in a tabs composition. */
@Component({
    selector: 'tumaet-ui-tab-panels',
    template: '<ng-content />',
    styleUrl: './tumaet-ui-tab-panels.component.scss',
    host: {
        class: 'tumaet-ui-tab-panels',
        '[attr.data-padded]': 'padded()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTabPanelsComponent {
    /** Disable when the containing surface already provides content padding. */
    readonly padded = input(true, { transform: booleanAttribute });
}
