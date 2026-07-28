import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiOverlayPlacement } from '../overlay/tum-ui-overlay.service';

const ARROW_BASE = 'absolute h-2 w-2 rotate-45 bg-tum-ui-surface-700';

const ARROW_POSITION: Record<TumUiOverlayPlacement, string> = {
    top: 'left-1/2 top-full -translate-x-1/2 -translate-y-1/2',
    bottom: 'left-1/2 bottom-full -translate-x-1/2 translate-y-1/2',
    left: 'top-1/2 left-full -translate-y-1/2 -translate-x-1/2',
    right: 'top-1/2 right-full -translate-y-1/2 translate-x-1/2',
};

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
        class: 'tum-ui-tooltip-bubble relative inline-block max-w-[12.5rem] rounded-md bg-tum-ui-surface-700 px-[0.75rem] py-[0.5rem] text-sm text-tum-ui-surface-0 shadow-md',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTooltipContentComponent {
    readonly text = input<string>('');
    readonly id = input<string>('');
    readonly placement = input<TumUiOverlayPlacement>('top');

    protected readonly arrowClasses = computed(() => `${ARROW_BASE} ${ARROW_POSITION[this.placement()]}`);
}
