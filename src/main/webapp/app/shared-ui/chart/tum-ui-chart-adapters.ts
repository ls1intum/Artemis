import { TumUiChartSeries } from '@tumaet/ui-angular';
import { ChartMultiSeriesEntry, ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';

/** Categories and series in the shape the tum-ui chart components consume. */
export interface TumUiChartData {
    labels: string[];
    series: TumUiChartSeries[];
}

/**
 * Converts a single data series (one value per category) into one bar or slice per entry.
 *
 * Colors are passed through untouched: the SVG charts accept `var(--token)` references directly, so
 * unlike the canvas charts they need no resolution step and follow theme changes on their own.
 */
export function singleSeriesChart(entries: ChartSeriesEntry[], colors: string[], seriesLabel?: string): TumUiChartData {
    return {
        labels: entries.map((entry) => entry.name),
        series: [
            {
                label: seriesLabel,
                data: entries.map((entry) => entry.value),
                // An empty palette would index with `% 0`, colouring every entry `undefined`. Leaving
                // `colors` off instead lets the chart fall back to its own default.
                colors: colors.length ? entries.map((_, index) => colors[index % colors.length]) : undefined,
                meta: entries,
            },
        ],
    };
}

function distinctSegmentNames(entries: ChartMultiSeriesEntry[]): string[] {
    return [...new Set(entries.flatMap((entry) => entry.series.map((item) => item.name)))];
}

function findSegment(entry: ChartMultiSeriesEntry, segmentName: string): ChartSeriesEntry | undefined {
    return entry.series.find((item) => item.name === segmentName);
}

/**
 * Converts multi-series data into a stacked bar chart: each entry becomes one bar, and each of its
 * series items becomes one stack segment. Categories without a value for a segment contribute 0.
 */
export function stackedBarChart(entries: ChartMultiSeriesEntry[], segmentColors: string[]): TumUiChartData {
    const segments = distinctSegmentNames(entries);
    return {
        labels: entries.map((entry) => entry.name),
        series: segments.map((segmentName, segmentIndex) => ({
            label: segmentName,
            data: entries.map((entry) => findSegment(entry, segmentName)?.value ?? 0),
            color: segmentColors.length ? segmentColors[segmentIndex % segmentColors.length] : undefined,
            meta: entries.map((entry) => findSegment(entry, segmentName)),
        })),
    };
}

/**
 * Like {@link stackedBarChart}, but expresses every segment as a percentage of its bar's total, so
 * that all bars fill the axis completely. Bars with a total of 0 keep all segments at 0.
 */
export function normalizedStackedBarChart(entries: ChartMultiSeriesEntry[], segmentColors: string[]): TumUiChartData {
    const data = stackedBarChart(entries, segmentColors);
    const totals = entries.map((entry) => entry.series.reduce((sum, item) => sum + item.value, 0));
    return {
        labels: data.labels,
        series: data.series.map((series) => ({
            label: series.label,
            color: series.color,
            meta: series.meta,
            data: series.data.map((value, index) => (totals[index] > 0 ? ((value ?? 0) / totals[index]) * 100 : 0)),
        })),
    };
}

/**
 * Converts multi-series data into a line chart, with one line per entry.
 *
 * When every entry carries the same series names in the same order, values are mapped positionally
 * so that duplicate labels — exercises that happen to share a title, for instance — stay distinct
 * points instead of collapsing onto one category.
 */
export function multiSeriesLineChart(entries: ChartMultiSeriesEntry[], colors: string[]): TumUiChartData {
    const firstSeries = entries[0]?.series ?? [];
    const aligned = entries.every((entry) => entry.series.length === firstSeries.length && entry.series.every((item, index) => item.name === firstSeries[index].name));

    if (aligned) {
        return {
            labels: firstSeries.map((item) => item.name),
            series: entries.map((entry, index) => ({
                label: entry.name,
                data: entry.series.map((item) => item.value),
                color: colors.length ? colors[index % colors.length] : undefined,
                meta: [...entry.series],
            })),
        };
    }

    const labels = distinctSegmentNames(entries);
    return {
        labels,
        series: entries.map((entry, index) => ({
            label: entry.name,
            data: labels.map((label) => findSegment(entry, label)?.value),
            color: colors.length ? colors[index % colors.length] : undefined,
            meta: labels.map((label) => findSegment(entry, label)),
        })),
    };
}

/** A flat dashed marker across the whole chart, such as an average line. */
export function referenceLineSeries(label: string, value: number, length: number, color: string): TumUiChartSeries {
    return {
        label,
        data: Array.from({ length }, () => value),
        color,
        referenceLine: true,
    };
}
