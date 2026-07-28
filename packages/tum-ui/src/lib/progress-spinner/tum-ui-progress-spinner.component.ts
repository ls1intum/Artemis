import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
    selector: 'tum-ui-progress-spinner',
    templateUrl: './tum-ui-progress-spinner.component.html',
    styleUrl: './tum-ui-progress-spinner.component.scss',
    host: {
        '[class]': 'hostClasses()',
        role: 'status',
        'aria-busy': 'true',
        '[attr.aria-label]': 'ariaLabel()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProgressSpinnerComponent {
    readonly strokeWidth = input<string | number>('2');

    readonly fill = input<string>('none');

    readonly animationDuration = input<string>('2s');

    readonly ariaLabel = input<string>();

    readonly styleClass = input<string>('');

    protected readonly hostClasses = computed(() => `tum-ui-progress-spinner ${this.styleClass()}`.trim());
}
