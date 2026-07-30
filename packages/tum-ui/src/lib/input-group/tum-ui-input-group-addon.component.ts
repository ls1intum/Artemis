import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tum-ui-input-group-addon',
    template: '<ng-content />',
    styleUrl: './tum-ui-input-group-addon.component.scss',
    host: {
        class:
            'tum-ui-input-group-addon tum:bg-control-background tum:text-muted tum:border-y tum:border-control-border ' +
            'tum:first:border-s tum:first:rounded-s-md tum:last:border-e tum:last:rounded-e-md',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiInputGroupAddonComponent {}
