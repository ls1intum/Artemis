import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * The tooltip bubble rendered inside the CDK overlay by {@link TumUiTooltipDirective}.
 * Styling matches the PrimeNG tooltip (small dark surface bubble) via Artemis token utilities.
 */
@Component({
    selector: 'tum-ui-tooltip-content',
    template: `{{ text() }}`,
    host: {
        role: 'tooltip',
        '[attr.id]': 'id()',
        class: 'tum-ui-tooltip-bubble inline-block max-w-xs rounded-md bg-surface-900 px-2 py-1 text-sm text-surface-0 shadow dark:bg-surface-700',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTooltipContentComponent {
    readonly text = input<string>('');
    readonly id = input<string>('');
}
