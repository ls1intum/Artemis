import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Hover tooltip, positioned by the chart in its own coordinate space. */
@Component({
    selector: 'tum-ui-chart-tooltip',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: {
        class: 'tum-ui-chart-tooltip tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:px-2 tum:py-1.5 tum:text-text tum:shadow-lg',
        role: 'tooltip',
        '[style.left.px]': 'x()',
        '[style.top.px]': 'y()',
        '[attr.data-below]': 'below()',
    },
    styles: `
        :host {
            display: block;
            position: absolute;
            z-index: 1;
            transform: translate(-50%, calc(-100% - 12px));
            max-width: 22rem;
            font-size: var(--tumaet-ui-font-size-xs);
            /*
             * The tooltip is drawn over the plot, so it must stay invisible to the pointer: catching it would
             * take the hover off the datum underneath, which hides the tooltip, which restores the hover, and
             * the two flicker against each other. Only the offset direction depends on which side it is on.
             */
            pointer-events: none;
        }
        :host([data-below='true']) {
            transform: translate(-50%, 12px);
        }
        .tum-ui-chart-tooltip-title {
            font-weight: 600;
        }
    `,
    template: `
        @if (title()) {
            <div class="tum-ui-chart-tooltip-title">{{ title() }}</div>
        }
        @for (line of lines(); track $index) {
            <div>{{ line }}</div>
        }
    `,
})
export class TumUiChartTooltipComponent {
    readonly title = input<string>();
    readonly lines = input<readonly string[]>([]);
    readonly x = input(0);
    readonly y = input(0);

    /** Renders below the pointer, for a datum too close to the top edge to leave room above. */
    readonly below = input(false);
}
