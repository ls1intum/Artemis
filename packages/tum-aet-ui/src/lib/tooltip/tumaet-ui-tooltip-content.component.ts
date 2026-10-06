import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumAetUiOverlayPlacement } from '../overlay/tumaet-ui-overlay.service';

const ARROW_BASE = 'tumaet:absolute tumaet:h-2 tumaet:w-2 tumaet:rotate-45 tumaet:bg-tooltip-background';

/** Which edge the arrow sits on, and the translate that centres it on its anchor point. */
const ARROW_SIDE: Record<TumAetUiOverlayPlacement, string> = {
    top: 'tumaet:top-full tumaet:-translate-x-1/2 tumaet:-translate-y-1/2',
    bottom: 'tumaet:bottom-full tumaet:-translate-x-1/2 tumaet:translate-y-1/2',
    left: 'tumaet:left-full tumaet:-translate-y-1/2 tumaet:-translate-x-1/2',
    right: 'tumaet:right-full tumaet:-translate-y-1/2 tumaet:translate-x-1/2',
};

/** The anchor point used until {@link TumAetUiTooltipContentComponent.arrowOffsetPx} says where the host actually is. */
const ARROW_CENTRE: Record<TumAetUiOverlayPlacement, string> = {
    top: 'tumaet:left-1/2',
    bottom: 'tumaet:left-1/2',
    left: 'tumaet:top-1/2',
    right: 'tumaet:top-1/2',
};

const BUBBLE_BASE =
    'tumaet-ui-tooltip-bubble tumaet:relative tumaet:inline-block tumaet:rounded-md tumaet:bg-tooltip-background tumaet:px-3 tumaet:py-2 tumaet:text-sm tumaet:text-tooltip tumaet:shadow-md';

/** A list of reasons needs more room than a one-line hint, so the two forms clamp differently. */
const BUBBLE_WIDTH = { text: 'tumaet:max-w-50', list: 'tumaet:max-w-100' };

@Component({
    selector: 'tumaet-ui-tooltip-content',
    template: `
        @if (items().length) {
            <ul class="tumaet:list-disc tumaet:ps-4 tumaet:text-start">
                @for (item of items(); track $index) {
                    <li class="tumaet:mt-1 tumaet:first:mt-0">{{ item }}</li>
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
        class: 'tumaet-ui-tooltip-bubble',
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTooltipContentComponent {
    readonly text = input<string>('');
    /** Rendered as a bulleted list instead of {@link text} when non-empty. */
    readonly items = input<readonly string[]>([]);
    readonly id = input<string>('');
    readonly placement = input<TumAetUiOverlayPlacement>('top');
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
