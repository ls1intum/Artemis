import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiOverlayPlacement } from '../overlay/tum-ui-overlay.service';

const ARROW_BASE = 'tum:absolute tum:h-2 tum:w-2 tum:rotate-45 tum:bg-tooltip-background';

/** Which edge the arrow sits on, and the translate that centres it on its anchor point. */
const ARROW_SIDE: Record<TumUiOverlayPlacement, string> = {
    top: 'tum:top-full tum:-translate-x-1/2 tum:-translate-y-1/2',
    bottom: 'tum:bottom-full tum:-translate-x-1/2 tum:translate-y-1/2',
    left: 'tum:left-full tum:-translate-y-1/2 tum:-translate-x-1/2',
    right: 'tum:right-full tum:-translate-y-1/2 tum:translate-x-1/2',
};

/** The anchor point used until {@link TumUiTooltipContentComponent.arrowOffsetPx} says where the host actually is. */
const ARROW_CENTRE: Record<TumUiOverlayPlacement, string> = {
    top: 'tum:left-1/2',
    bottom: 'tum:left-1/2',
    left: 'tum:top-1/2',
    right: 'tum:top-1/2',
};

const BUBBLE_BASE = 'tum-ui-tooltip-bubble tum:relative tum:inline-block tum:rounded-md tum:bg-tooltip-background tum:px-3 tum:py-2 tum:text-sm tum:text-tooltip tum:shadow-md';

/** A list of reasons needs more room than a one-line hint, so the two forms clamp differently. */
const BUBBLE_WIDTH = { text: 'tum:max-w-50', list: 'tum:max-w-100' };

@Component({
    selector: 'tum-ui-tooltip-content',
    template: `
        @if (items().length) {
            <ul class="tum:list-disc tum:ps-4 tum:text-start">
                @for (item of items(); track $index) {
                    <li class="tum:mt-1 tum:first:mt-0">{{ item }}</li>
                }
            </ul>
        } @else {
            {{ text() }}
        }
        <span aria-hidden="true" [class]="arrowClasses()" [style]="arrowStyle()"></span>
    `,
    host: {
        role: 'tooltip',
        '[attr.id]': 'id()',
        // The identifier is also static so the bubble is queryable before the first change detection run.
        class: 'tum-ui-tooltip-bubble',
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTooltipContentComponent {
    readonly text = input<string>('');
    /** Rendered as a bulleted list instead of {@link text} when non-empty. */
    readonly items = input<readonly string[]>([]);
    readonly id = input<string>('');
    readonly placement = input<TumUiOverlayPlacement>('top');
    /**
     * Where the arrow belongs along the bubble's edge, in pixels from its left or top. The bubble is pushed sideways
     * to stay on screen, so a centred arrow stops pointing at the host; the directive measures the host and reports
     * the point here. Undefined until measured, and then the arrow is centred as before.
     */
    readonly arrowOffsetPx = input<number | undefined>(undefined);

    private readonly isHorizontal = computed(() => this.placement() === 'top' || this.placement() === 'bottom');

    protected readonly arrowClasses = computed(() => {
        const anchor = this.arrowOffsetPx() === undefined ? ` ${ARROW_CENTRE[this.placement()]}` : '';
        return `${ARROW_BASE} ${ARROW_SIDE[this.placement()]}${anchor}`;
    });

    protected readonly arrowStyle = computed<Record<string, string> | null>(() => {
        const offset = this.arrowOffsetPx();
        if (offset === undefined) {
            return null;
        }
        return { [this.isHorizontal() ? 'left' : 'top']: `${offset}px` };
    });

    protected readonly hostClasses = computed(() => `${BUBBLE_BASE} ${this.items().length ? BUBBLE_WIDTH.list : BUBBLE_WIDTH.text}`);
}
