import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ChartLegendItem } from './tum-ui-chart.frame';
import { TumUiChartLegendPosition } from './tum-ui-chart.types';

/** Legend swatches for a chart's series. Rendered as HTML so the labels stay selectable and wrap. */
@Component({
    selector: 'tum-ui-chart-legend',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'tum-ui-chart-legend', '[attr.data-position]': 'position()' },
    styles: `
        :host {
            display: block;
            align-self: center;
            font-size: var(--tumaet-ui-font-size-xs);
            color: var(--tumaet-ui-text-color);
        }
        .tum-ui-chart-legend-list {
            display: flex;
            flex-direction: column;
            gap: calc(var(--tumaet-ui-spacing) * 1);
            margin: 0;
            padding: 0;
            list-style: none;
        }
        :host([data-position='top']) .tum-ui-chart-legend-list,
        :host([data-position='bottom']) .tum-ui-chart-legend-list {
            flex-direction: row;
            flex-wrap: wrap;
            justify-content: center;
        }
        .tum-ui-chart-legend-item {
            display: flex;
            align-items: center;
            gap: calc(var(--tumaet-ui-spacing) * 1);
            white-space: nowrap;
        }
        .tum-ui-chart-legend-swatch {
            width: 10px;
            height: 10px;
            border-radius: var(--tumaet-ui-radius-sm);
            flex: none;
        }
    `,
    template: `
        <ul class="tum-ui-chart-legend-list">
            @for (item of items(); track item.key) {
                <li class="tum-ui-chart-legend-item">
                    <span class="tum-ui-chart-legend-swatch" [style.background]="item.color"></span>
                    <span>{{ item.label }}</span>
                </li>
            }
        </ul>
    `,
})
export class TumUiChartLegendComponent {
    readonly items = input<readonly ChartLegendItem[]>([]);

    /** Drives the layout: a legend above or below the plot lays its entries out in a row. */
    readonly position = input<TumUiChartLegendPosition>('right');
}
