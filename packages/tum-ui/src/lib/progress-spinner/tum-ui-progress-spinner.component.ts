import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
    selector: 'tum-ui-progress-spinner',
    templateUrl: './tum-ui-progress-spinner.component.html',
    styleUrl: './tum-ui-progress-spinner.component.scss',
    host: {
        class: 'tum-ui-progress-spinner',
        role: 'status',
        'aria-busy': 'true',
        '[attr.aria-label]': 'ariaLabel()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProgressSpinnerComponent {
    readonly ariaLabel = input<string>();
}
