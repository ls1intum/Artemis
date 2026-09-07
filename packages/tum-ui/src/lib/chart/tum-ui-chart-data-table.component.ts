import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export interface TumUiChartDataTableRow {
    label: string;
    values: { seriesLabel?: string; value?: number }[];
}

/**
 * Mirrors a chart's values as a table for assistive technology.
 *
 * It is excluded from text selection so that copying the chart's own labels does not also pick up a
 * duplicate of every value.
 */
@Component({
    selector: 'tum-ui-chart-data-table',
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'tum-ui-chart-data-table' },
    styles: `
        :host {
            position: absolute;
            width: 1px;
            height: 1px;
            margin: -1px;
            padding: 0;
            overflow: hidden;
            clip-path: inset(50%);
            white-space: nowrap;
            border: 0;
            user-select: none;
        }
    `,
    template: `
        <table>
            <caption>
                {{
                    caption()
                }}
            </caption>
            <tbody>
                @for (row of rows(); track $index) {
                    <tr>
                        <th scope="row">{{ row.label }}</th>
                        @for (cell of row.values; track $index) {
                            <td>{{ cell.seriesLabel ? cell.seriesLabel + ': ' : '' }}{{ cell.value }}</td>
                        }
                    </tr>
                }
            </tbody>
        </table>
    `,
})
export class TumUiChartDataTableComponent {
    readonly caption = input<string>();
    readonly rows = input<readonly TumUiChartDataTableRow[]>([]);
}
