import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tum-ui-input-group-addon',
    template: '<ng-content />',
    styleUrl: './tum-ui-input-group-addon.component.scss',
    host: {
        class:
            'tum-ui-input-group-addon tum:bg-tum-ui-surface-0 tum:text-tum-ui-muted tum:border-y tum:border-tum-ui-surface-300 ' +
            'tum:first:border-s tum:first:rounded-s-md tum:last:border-e tum:last:rounded-e-md ' +
            'tum:dark:bg-tum-ui-surface-950 tum:dark:border-tum-ui-surface-600',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiInputGroupAddonComponent {}
