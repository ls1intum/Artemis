import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tum-ui-button-group',
    template: '<ng-content />',
    host: {
        role: 'group',
        class: 'tum-ui-button-group',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonGroupComponent {}
