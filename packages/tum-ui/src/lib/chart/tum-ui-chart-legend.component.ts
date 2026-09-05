import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ChartLegendItem } from './tum-ui-chart.frame';
import { TumUiChartLegendPosition } from './tum-ui-chart.types';

/**
 * Legend for a chart's series or slices.
 *
 * Entries are buttons: clicking one hides or shows what it names, which is how a reader compares two
 * lines out of five. Rendering them as real buttons rather than painted swatches also makes the
 * legend keyboard operable, which the canvas charts never were.
 */
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
            /* WCAG 2.2 target size: a legend entry is a control, so it needs at least 24px to hit. */
            min-height: 24px;
            padding: 0 calc(var(--tumaet-ui-spacing) * 1);
            border: 0;
            background: none;
            color: inherit;
            font: inherit;
            cursor: pointer;
        }
        .tum-ui-chart-legend-item[aria-pressed='false'] {
            opacity: 0.45;
            text-decoration: line-through;
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
                <li>
                    <button type="button" class="tum-ui-chart-legend-item" [attr.aria-pressed]="!item.hidden" (click)="toggleEntry.emit(item.key)">
                        <span class="tum-ui-chart-legend-swatch" [style.background]="item.color"></span>
                        <span>{{ item.label }}</span>
                    </button>
                </li>
            }
        </ul>
    `,
})
export class TumUiChartLegendComponent {
    readonly items = input<readonly ChartLegendItem[]>([]);

    /** Drives the layout: a legend above or below the plot lays its entries out in a row. */
    readonly position = input<TumUiChartLegendPosition>('right');

    /** Emits the key of the entry the reader clicked, so the chart can hide or show it. */
    readonly toggleEntry = output<string>();
}
