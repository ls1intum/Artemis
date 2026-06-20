import { ChartMultiSeriesEntry } from 'app/shared-ui/chart/chart-data.model';

export interface FeedbackChartData {
    xScaleMax: number;
    /** Raw segment colors, possibly CSS variable references like 'var(--bs-success)' — resolve before rendering. */
    colors: string[];
    results: ChartMultiSeriesEntry[];
}
