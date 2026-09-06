import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ChartAxisTitle, ChartGridLine, ChartTick } from './tum-ui-chart.frame';

/**
 * Renders the grid, tick labels and axis titles of a cartesian chart.
 *
 * Applied to an `<svg:g>` that is already translated into the plot's coordinate system, so every
 * coordinate it receives is relative to the plot origin.
 */
@Component({
    selector: 'g[tumUiChartAxes]',
    changeDetection: ChangeDetectionStrategy.OnPush,
    styles: `
        .tum-ui-chart-grid {
            stroke: var(--tumaet-ui-border-color);
            stroke-width: 1;
            shape-rendering: crispedges;
        }
        .tum-ui-chart-tick {
            fill: var(--tumaet-ui-muted-color);
            font-size: var(--tumaet-ui-font-size-xs);
            font-family: inherit;
        }
        .tum-ui-chart-axis-title {
            fill: var(--tumaet-ui-text-color);
            font-size: var(--tumaet-ui-font-size-sm);
            font-family: inherit;
        }
    `,
    template: `
        @for (gridLine of gridLines(); track gridLine.key) {
            <svg:line class="tum-ui-chart-grid" [attr.x1]="gridLine.x1" [attr.y1]="gridLine.y1" [attr.x2]="gridLine.x2" [attr.y2]="gridLine.y2" />
        }
        @for (tick of ticks(); track tick.key) {
            <svg:text
                class="tum-ui-chart-tick"
                [attr.x]="tick.x"
                [attr.y]="tick.y"
                [attr.text-anchor]="tick.anchor"
                [attr.transform]="tick.rotate ? 'rotate(' + tick.rotate + ' ' + tick.x + ' ' + tick.y + ')' : undefined"
            >
                {{ tick.text }}
            </svg:text>
        }
        @for (title of titles(); track $index) {
            <svg:text
                class="tum-ui-chart-axis-title"
                [attr.x]="title.x"
                [attr.y]="title.y"
                text-anchor="middle"
                [attr.transform]="title.rotate ? 'rotate(' + title.rotate + ' ' + title.x + ' ' + title.y + ')' : undefined"
            >
                {{ title.text }}
            </svg:text>
        }
    `,
})
export class TumUiChartAxesComponent {
    readonly gridLines = input<readonly ChartGridLine[]>([]);
    readonly ticks = input<readonly ChartTick[]>([]);
    readonly titles = input<readonly ChartAxisTitle[]>([]);
}
