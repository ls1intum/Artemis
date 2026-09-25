import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tumaet-ui-button-group',
    template: '<ng-content />',
    host: {
        role: 'group',
        class: 'tumaet-ui-button-group',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiButtonGroupComponent {}
