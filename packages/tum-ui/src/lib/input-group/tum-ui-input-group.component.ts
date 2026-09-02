import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tum-ui-input-group',
    template: '<ng-content />',
    styleUrl: './tum-ui-input-group.component.scss',
    host: {
        '[attr.data-slot]': '"input-group"',
        class: 'tum-ui-input-group',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiInputGroupComponent {}
