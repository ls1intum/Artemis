import { ChartType } from 'chart.js';

/**
 * A single data point of a chart series, e.g. one bar or one pie slice.
 * The index signature allows components to attach arbitrary metadata (e.g. absolute values,
 * exercise titles, entity ids) that tooltip callbacks and select handlers can read back via
 * the dataset's `meta` array.
 */
export interface ChartSeriesEntry {
    name: string;
    value: number;
    [extra: string]: unknown;
}

/**
 * A named series of data points. Depending on the chart family, one entry maps to one line
 * (line charts) or to one category/bar whose series items are the stack segments (stacked bars).
 */
export interface ChartMultiSeriesEntry {
    name: string;
    series: ChartSeriesEntry[];
}

declare module 'chart.js' {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    interface ChartDatasetProperties<TType extends ChartType, TData> {
        /**
         * The original {@link ChartSeriesEntry} objects backing this dataset, index-aligned with `data`.
         * Carries metadata for tooltip callbacks and select handlers. Entries can be undefined when a
         * category has no value in this dataset (e.g. sparse stacked bars).
         */
        meta?: (ChartSeriesEntry | undefined)[];
        /**
         * Marks a dataset as a decorative reference line (e.g. an average marker) that should be
         * excluded from the legend and from select events.
         */
        referenceLine?: boolean;
    }
}
