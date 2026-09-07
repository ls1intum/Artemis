/**
 * A single data point of a chart series, e.g. one bar or one pie slice.
 * The index signature allows components to attach arbitrary metadata (e.g. absolute values,
 * exercise titles, entity ids) that tooltip callbacks and select handlers read back via the
 * datum's `meta`.
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
