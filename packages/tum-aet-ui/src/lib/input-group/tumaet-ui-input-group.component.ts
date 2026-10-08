import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tumaet-ui-input-group',
    template: '<ng-content />',
    styleUrl: './tumaet-ui-input-group.component.scss',
    host: {
        class: 'tumaet-ui-input-group',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiInputGroupComponent {}
