import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
    selector: 'tumaet-ui-progress-spinner',
    templateUrl: './tumaet-ui-progress-spinner.component.html',
    styleUrl: './tumaet-ui-progress-spinner.component.scss',
    host: {
        class: 'tumaet-ui-progress-spinner',
        role: 'status',
        'aria-busy': 'true',
        '[attr.aria-label]': 'ariaLabel()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiProgressSpinnerComponent {
    readonly ariaLabel = input<string>();
}
