import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ChartLegendItem } from './tum-ui-chart.frame';

/** Legend swatches for a chart's series. Rendered as HTML so the labels stay selectable and wrap. */
@Component({
    selector: 'tum-ui-chart-legend',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'tum-ui-chart-legend' },
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
        :host-context([data-legend='top']) .tum-ui-chart-legend-list,
        :host-context([data-legend='bottom']) .tum-ui-chart-legend-list {
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
}
