import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input, numberAttribute } from '@angular/core';

export type TumUiProgressBarSeverity = 'primary' | 'success' | 'warn' | 'danger' | 'info';
export type TumUiProgressBarSize = 'small' | 'default';

@Component({
    selector: 'tum-ui-progress-bar',
    templateUrl: './tum-ui-progress-bar.component.html',
    styleUrl: './tum-ui-progress-bar.component.scss',
    host: {
        class: 'tum-ui-progress-bar tum:bg-border',
        role: 'progressbar',
        '[attr.data-size]': 'size()',
        '[attr.aria-valuemin]': '0',
        '[attr.aria-valuemax]': '100',
        '[attr.aria-valuenow]': 'normalizedValue()',
        '[attr.aria-label]': 'ariaLabel()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProgressBarComponent {
    readonly value = input(0, { transform: numberAttribute });

    readonly ariaLabel = input<string>();

    readonly showValue = input(true, { transform: booleanAttribute });

    /** Track height. `small` is a slim rail for dense contexts such as table cells and has no room for the inline label. */
    readonly size = input<TumUiProgressBarSize>('default');

    /** Semantic color of the filled track. */
    readonly severity = input<TumUiProgressBarSeverity>('primary');

    readonly unit = input<string>('%');

    protected readonly normalizedValue = computed(() => {
        const value = this.value();
        return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
    });
}
