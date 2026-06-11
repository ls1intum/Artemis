import { ChartData, ChartDataset } from 'chart.js';
import { ChartMultiSeriesEntry, ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';

/**
 * Converts a single data series (one value per category) to chart.js data for bar, pie, and
 * doughnut charts. The original entries are kept on the dataset as `meta` so tooltip callbacks
 * and select handlers can access attached metadata.
 *
 * @param entries the data points; one label/value pair per category or slice
 * @param colors concrete (already resolved) colors, one per entry; cycled if shorter
 * @param datasetLabel optional dataset label, shown in default tooltips
 */
export function singleSeriesChartData(entries: ChartSeriesEntry[], colors: string[], datasetLabel?: string): ChartData<'bar' | 'pie' | 'doughnut', number[], string> {
    return {
        labels: entries.map((entry) => entry.name),
        datasets: [
            {
                label: datasetLabel,
                data: entries.map((entry) => entry.value),
                backgroundColor: entries.map((entry, index) => colors[index % colors.length]),
                meta: entries,
            },
        ],
    };
}

function findSegment(entry: ChartMultiSeriesEntry, segmentName: string): ChartSeriesEntry | undefined {
    return entry.series.find((item) => item.name === segmentName);
}

/**
 * Converts ngx-style multi-series data to a stacked bar chart.js structure.
 * Each entry becomes one bar (category); each item of an entry's series becomes one stack segment.
 * One dataset is created per distinct segment name (in order of first occurrence); categories
 * without a value for a segment get 0.
 *
 * @param entries one entry per bar, whose series items are the stack segments
 * @param segmentColors concrete colors, one per distinct segment name; cycled if shorter
 */
export function multiSeriesToStackedBarData(entries: ChartMultiSeriesEntry[], segmentColors: string[]): ChartData<'bar', number[], string> {
    const segmentNames: string[] = [];
    for (const entry of entries) {
        for (const item of entry.series) {
            if (!segmentNames.includes(item.name)) {
                segmentNames.push(item.name);
            }
        }
    }
    return {
        labels: entries.map((entry) => entry.name),
        datasets: segmentNames.map((segmentName, segmentIndex) => ({
            label: segmentName,
            data: entries.map((entry) => findSegment(entry, segmentName)?.value ?? 0),
            backgroundColor: segmentColors[segmentIndex % segmentColors.length],
            meta: entries.map((entry) => findSegment(entry, segmentName)),
        })),
    };
}

/**
 * Like {@link multiSeriesToStackedBarData}, but converts every bar's segment values to percentages
 * of that bar's total (chart.js has no built-in normalized stacking). Bars with a total of 0 keep
 * all segments at 0. The original entries remain available via the datasets' `meta`.
 */
export function multiSeriesToNormalizedStackedBarData(entries: ChartMultiSeriesEntry[], segmentColors: string[]): ChartData<'bar', number[], string> {
    const data = multiSeriesToStackedBarData(entries, segmentColors);
    const totals = entries.map((entry) => entry.series.reduce((sum, item) => sum + item.value, 0));
    for (const dataset of data.datasets) {
        dataset.data = dataset.data.map((value, index) => (totals[index] > 0 ? (value / totals[index]) * 100 : 0));
    }
    return data;
}

/**
 * Converts ngx-style multi-series data to a line chart.js structure.
 * Each entry becomes one line (dataset); the labels are the union of all series item names in
 * order of first occurrence. Points missing in a series are null (visualized according to spanGaps).
 *
 * @param entries one entry per line
 * @param colors concrete colors, one per line; cycled if shorter
 * @param options monotone applies monotone cubic interpolation (equivalent of d3 curveMonotoneX);
 *                spanGaps connects the line across null points
 */
export function multiSeriesToLineData(
    entries: ChartMultiSeriesEntry[],
    colors: string[],
    options?: { monotone?: boolean; spanGaps?: boolean },
): ChartData<'line', (number | null)[], string> {
    const labels: string[] = [];
    for (const entry of entries) {
        for (const item of entry.series) {
            if (!labels.includes(item.name)) {
                labels.push(item.name);
            }
        }
    }
    return {
        labels,
        datasets: entries.map((entry, index) => {
            const color = colors[index % colors.length];
            return {
                label: entry.name,
                data: labels.map((label) => findSegment(entry, label)?.value ?? null),
                borderColor: color,
                backgroundColor: color,
                pointBackgroundColor: color,
                fill: false,
                cubicInterpolationMode: options?.monotone ? ('monotone' as const) : ('default' as const),
                spanGaps: options?.spanGaps ?? false,
                meta: labels.map((label) => findSegment(entry, label)),
            };
        }),
    };
}

/**
 * Creates a flat dashed line dataset without points, replacing ngx-charts' referenceLines
 * (e.g. an average marker). Marked with `referenceLine` so the legend filter and select-event
 * mapping ignore it.
 *
 * @param label dataset label (shown in tooltips if not disabled)
 * @param value the constant y-value of the line
 * @param length number of data points (must match the chart's label count)
 * @param color concrete line color
 */
export function referenceLineDataset(label: string, value: number, length: number, color: string): ChartDataset<'line', (number | null)[]> {
    return {
        label,
        data: Array.from({ length }, () => value),
        borderColor: color,
        borderDash: [5, 5],
        borderWidth: 1.5,
        pointRadius: 0,
        pointHitRadius: 0,
        fill: false,
        referenceLine: true,
    };
}
