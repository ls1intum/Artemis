import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
    selector: 'tum-ui-progress-bar',
    templateUrl: './tum-ui-progress-bar.component.html',
    styleUrl: './tum-ui-progress-bar.component.scss',
    host: {
        '[class]': 'hostClasses()',
        role: 'progressbar',
        '[attr.aria-valuemin]': '0',
        '[attr.aria-valuemax]': '100',
        '[attr.aria-valuenow]': 'normalizedValue()',
        '[attr.aria-label]': 'ariaLabel()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProgressBarComponent {
    readonly value = input<number>(0);

    readonly ariaLabel = input<string>();

    readonly showValue = input(true);

    readonly color = input<string>();

    readonly unit = input<string>('%');

    readonly styleClass = input<string>('');

    protected readonly normalizedValue = computed(() => {
        const value = this.value();
        return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
    });
    protected readonly hostClasses = computed(() => `tum-ui-progress-bar tum:bg-border ${this.styleClass()}`.trim());
}
