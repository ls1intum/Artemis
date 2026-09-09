import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

/** Layout container for the panels in a tabs composition. */
@Component({
    selector: 'tum-ui-tab-panels',
    template: '<ng-content />',
    styleUrl: './tum-ui-tab-panels.component.scss',
    host: {
        class: 'tum-ui-tab-panels',
        '[attr.data-padded]': 'padded()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTabPanelsComponent {
    /** Disable when the containing surface already provides content padding. */
    readonly padded = input(true, { transform: booleanAttribute });
}
