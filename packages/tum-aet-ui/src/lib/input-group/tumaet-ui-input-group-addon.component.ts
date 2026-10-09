import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
    selector: 'tumaet-ui-input-group-addon',
    template: '<ng-content />',
    styleUrl: './tumaet-ui-input-group-addon.component.scss',
    host: {
        class:
            'tumaet-ui-input-group-addon tumaet:bg-control-background tumaet:text-muted tumaet:border-y ' +
            'tumaet:first:border-s tumaet:first:rounded-s-md tumaet:last:border-e tumaet:last:rounded-e-md',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiInputGroupAddonComponent {}
