import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiOverlayPlacement } from 'app/shared-ui/tum-ui/overlay/tum-ui-overlay.service';

/**
 * The tooltip bubble rendered inside the CDK overlay by {@link TumUiTooltipDirective}: a small dark
 * surface with a directional caret, styled with Artemis token utilities. The caret sits on the edge
 * facing the trigger; the directive keeps {@link placement} in sync with the overlay's actually-applied
 * position, so it stays correct even when CDK flips the overlay to the opposite side at a viewport edge.
 */
@Component({
    selector: 'tum-ui-tooltip-content',
    template: `{{ text() }}<span aria-hidden="true" [class]="arrowClasses()"></span>`,
    host: {
        role: 'tooltip',
        '[attr.id]': 'id()',
        class: 'tum-ui-tooltip-bubble relative inline-block max-w-xs rounded-md bg-surface-900 px-2 py-1 text-sm text-surface-0 shadow-md dark:bg-surface-700',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTooltipContentComponent {
    readonly text = input<string>('');
    readonly id = input<string>('');
    readonly placement = input<TumUiOverlayPlacement>('top');

    // A rotated square sharing the bubble background; positioned on the edge facing the trigger so half of
    // it sticks out as a caret. Keep the background in sync with the host bubble background above.
    private static readonly ARROW_BASE = 'absolute h-2 w-2 rotate-45 bg-surface-900 dark:bg-surface-700';

    protected readonly arrowClasses = computed(() => {
        const position =
            {
                top: 'left-1/2 top-full -translate-x-1/2 -translate-y-1/2',
                bottom: 'left-1/2 bottom-full -translate-x-1/2 translate-y-1/2',
                left: 'top-1/2 left-full -translate-y-1/2 -translate-x-1/2',
                right: 'top-1/2 right-full -translate-y-1/2 translate-x-1/2',
            }[this.placement()] ?? '';
        return `${TumUiTooltipContentComponent.ARROW_BASE} ${position}`;
    });
}
