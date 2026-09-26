import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ChartLegendItem } from './tumaet-ui-chart.frame';
import { TumAetUiChartLegendPosition } from './tumaet-ui-chart.types';

/** Keyboard-operable legend whose buttons toggle the visibility of series or slices. */
@Component({
    selector: 'tumaet-ui-chart-legend',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'tumaet-ui-chart-legend', '[attr.data-position]': 'position()' },
    styles: `
        :host {
            display: block;
            align-self: center;
            font-size: var(--tumaet-ui-font-size-xs);
            color: var(--tumaet-ui-text-color);
        }
        .tumaet-ui-chart-legend-list {
            display: flex;
            flex-direction: column;
            gap: calc(var(--tumaet-ui-spacing) * 1);
            margin: 0;
            padding: 0;
            list-style: none;
        }
        :host([data-position='top']) .tumaet-ui-chart-legend-list,
        :host([data-position='bottom']) .tumaet-ui-chart-legend-list {
            flex-direction: row;
            flex-wrap: wrap;
            justify-content: center;
        }
        .tumaet-ui-chart-legend-item {
            display: flex;
            align-items: center;
            gap: calc(var(--tumaet-ui-spacing) * 1);
            white-space: nowrap;
            /* WCAG 2.2 target size: a legend entry is a control, so it needs at least 24px to hit. */
            min-height: 24px;
            padding: 0 calc(var(--tumaet-ui-spacing) * 1);
            border: 0;
            background: none;
            color: inherit;
            font: inherit;
            cursor: pointer;
        }
        .tumaet-ui-chart-legend-item[aria-pressed='false'] {
            opacity: 0.45;
            text-decoration: line-through;
        }
        .tumaet-ui-chart-legend-swatch {
            width: 10px;
            height: 10px;
            border-radius: var(--tumaet-ui-radius-sm);
            flex: none;
        }
    `,
    template: `
        <ul class="tumaet-ui-chart-legend-list">
            @for (item of items(); track item.key) {
                <li>
                    <button type="button" class="tumaet-ui-chart-legend-item" [attr.aria-pressed]="!item.hidden" (click)="toggleEntry.emit(item.key)">
                        <span class="tumaet-ui-chart-legend-swatch" [style.background]="item.color"></span>
                        <span>{{ item.label }}</span>
                    </button>
                </li>
            }
        </ul>
    `,
})
export class TumAetUiChartLegendComponent {
    readonly items = input<readonly ChartLegendItem[]>([]);

    /** Drives the layout: a legend above or below the plot lays its entries out in a row. */
    readonly position = input<TumAetUiChartLegendPosition>('right');

    /** Emits the key of the entry the reader clicked, so the chart can hide or show it. */
    readonly toggleEntry = output<string>();
}
