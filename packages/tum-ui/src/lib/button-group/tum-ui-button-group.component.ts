import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tum-ui-button-group',
    template: '<ng-content />',
    host: {
        '[attr.data-slot]': '"button-group"',
        role: 'group',
        class: 'tum-ui-button-group',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonGroupComponent {}
