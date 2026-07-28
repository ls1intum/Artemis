import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tum-ui-input-group-addon',
    template: '<ng-content />',
    styleUrl: './tum-ui-input-group-addon.component.scss',
    host: {
        class:
            'tum-ui-input-group-addon bg-tum-ui-surface-0 text-tum-ui-muted border-y border-tum-ui-surface-300 ' +
            'first:border-s first:rounded-s-md last:border-e last:rounded-e-md ' +
            'dark:bg-tum-ui-surface-950 dark:border-tum-ui-surface-600',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiInputGroupAddonComponent {}
