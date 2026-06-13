import { ChartData, ChartDataset, ChartOptions, ChartType, LegendItem, Scale, TooltipItem } from 'chart.js';
import { Context } from 'chartjs-plugin-datalabels';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';

export interface ChartAxisConfig {
    label?: string;
    min?: number;
    max?: number;
    /** Formats tick labels; receives the category label (category axis) or the numeric tick value (linear axis). */
    tickFormatter?: (value: number | string) => string;
    display?: boolean;
}

/** Tooltip text callbacks; metadata of the hovered point is available via item.dataset.meta?.[item.dataIndex]. */
export interface ChartTooltipContent<TType extends ChartType = ChartType> {
    title?: (items: TooltipItem<TType>[]) => string | string[];
    label?: (item: TooltipItem<TType>) => string | string[];
    afterBody?: (items: TooltipItem<TType>[]) => string | string[];
}

export interface BaseChartConfig<TType extends ChartType = ChartType> {
    xAxis?: ChartAxisConfig;
    yAxis?: ChartAxisConfig;
    /** ngx-charts default legend position was 'right'. Defaults to hidden. */
    legend?: boolean | { position?: 'top' | 'right' | 'bottom' | 'left' };
    /** false disables tooltips entirely; undefined keeps chart.js default tooltips. */
    tooltip?: false | ChartTooltipContent<TType>;
}

export interface BarChartConfig extends BaseChartConfig<'bar'> {
    /** Renders horizontal bars (indexAxis 'y'). */
    horizontal?: boolean;
    /** Stacks datasets on both axes. */
    stacked?: boolean;
    /** Value axis in percent: ticks get a '%' suffix and the axis is capped at 100 (for normalized data). */
    percentScale?: boolean;
    /** Persistent labels rendered on the bars via chartjs-plugin-datalabels — pass the plugin to <p-chart [plugins]>. */
    dataLabels?: { formatter: (value: number, context: Context) => string };
    /** Caps the bar's cross-axis thickness in px. Use for slim summary bars (e.g. the feedback score bar) so they don't stretch to fill the container height. */
    maxBarThickness?: number;
}

export type LineChartConfig = BaseChartConfig<'line'>;

export interface DoughnutChartConfig {
    /** Width of the ring as fraction of the radius, as in ngx-charts (default 0.25 → cutout '75%'). Pass 1 for a full pie chart. */
    arcWidth?: number;
    /** Outer radius as a px number or '%' of the available space. Defaults to chart.js '100%' (fills the canvas); use e.g. '75%' to leave an ngx-charts-style margin in tight containers. */
    radius?: number | string;
    legend?: BaseChartConfig['legend'];
    tooltip?: BaseChartConfig<'doughnut'>['tooltip'];
}

/** Normalized payload of a p-chart (onDataSelect) event, mirroring what ngx-charts (select) provided. */
export interface ChartSelectEvent {
    datasetIndex: number;
    index: number;
    /** The category label at the clicked index — ngx event.name. */
    label?: string;
    /** The dataset label — ngx event.series. */
    datasetLabel?: string;
    value?: number;
    /** The original series entry incl. attached metadata, if provided by the data adapter. */
    meta?: ChartSeriesEntry;
}

/**
 * Maps the payload of p-chart's (onDataSelect) output to a {@link ChartSelectEvent}.
 * Returns undefined for clicks that did not hit a data element or hit a reference line.
 */
export function toChartSelectEvent(event: { element?: { datasetIndex: number; index: number } }, data: ChartData): ChartSelectEvent | undefined {
    const element = event?.element;
    if (!element) {
        return undefined;
    }
    const dataset = data.datasets?.[element.datasetIndex];
    if (!dataset || dataset.referenceLine) {
        return undefined;
    }
    const rawValue = dataset.data?.[element.index];
    const rawLabel = data.labels?.[element.index];
    return {
        datasetIndex: element.datasetIndex,
        index: element.index,
        label: typeof rawLabel === 'string' ? rawLabel : undefined,
        datasetLabel: dataset.label,
        value: typeof rawValue === 'number' ? rawValue : undefined,
        meta: dataset.meta?.[element.index],
    };
}

function tickCallback(formatter: (value: number | string) => string, isCategoryAxis: boolean) {
    return function (this: Scale, value: number | string): string {
        // On category axes chart.js passes the index, not the label
        const raw = isCategoryAxis && typeof value === 'number' ? this.getLabelForValue(value) : value;
        return formatter(raw);
    };
}

function buildScale(axis: ChartAxisConfig | undefined, options: { stacked?: boolean; isCategoryAxis: boolean; percent?: boolean }) {
    const percentFormatter = (value: number | string) => `${value}%`;
    const formatter = axis?.tickFormatter ?? (options.percent ? percentFormatter : undefined);
    return {
        display: axis?.display ?? true,
        stacked: options.stacked ?? false,
        min: axis?.min,
        max: axis?.max ?? (options.percent ? 100 : undefined),
        title: axis?.label ? { display: true, text: axis.label } : undefined,
        ticks: formatter ? { callback: tickCallback(formatter, options.isCategoryAxis) } : undefined,
        grid: options.isCategoryAxis ? { display: false } : undefined,
    };
}

function buildLegend(legend: BaseChartConfig['legend']) {
    if (!legend) {
        return { display: false };
    }
    return {
        display: true,
        position: typeof legend === 'object' ? (legend.position ?? 'right') : ('right' as const),
        labels: {
            filter: (item: LegendItem, data: ChartData) => item.datasetIndex === undefined || !data.datasets[item.datasetIndex]?.referenceLine,
        },
    };
}

function buildTooltip<TType extends ChartType>(tooltip: false | ChartTooltipContent<TType> | undefined) {
    if (tooltip === false) {
        return { enabled: false };
    }
    // reference line datasets are decorative and never part of tooltips
    const referenceLineFilter = (item: TooltipItem<TType>) => !(item.dataset as ChartDataset).referenceLine;
    if (!tooltip) {
        return { filter: referenceLineFilter };
    }
    return {
        filter: referenceLineFilter,
        callbacks: {
            title: tooltip.title,
            label: tooltip.label,
            afterBody: tooltip.afterBody,
        },
    };
}

/**
 * Builds chart.js options for vertical, horizontal, stacked, and normalized bar charts.
 * Text and grid colors come from the theme-synced chart.js defaults (see ChartColorService).
 */
export function barChartOptions(config: BarChartConfig): ChartOptions<'bar'> {
    const horizontal = config.horizontal ?? false;
    const valueAxisPercent = config.percentScale ?? false;
    return {
        indexAxis: horizontal ? 'y' : 'x',
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        datasets: config.maxBarThickness ? { bar: { maxBarThickness: config.maxBarThickness } } : undefined,
        scales: {
            x: buildScale(config.xAxis, { stacked: config.stacked, isCategoryAxis: !horizontal, percent: horizontal && valueAxisPercent }),
            y: buildScale(config.yAxis, { stacked: config.stacked, isCategoryAxis: horizontal, percent: !horizontal && valueAxisPercent }),
        },
        plugins: {
            legend: buildLegend(config.legend),
            tooltip: buildTooltip(config.tooltip),
            datalabels: config.dataLabels
                ? {
                      display: true,
                      anchor: 'end' as const,
                      align: 'end' as const,
                      clamp: true,
                      formatter: config.dataLabels.formatter,
                  }
                : { display: false },
        },
    } as ChartOptions<'bar'>;
}

/**
 * Builds chart.js options for line charts. Tooltips use index mode (all series at the hovered
 * x-position), matching the ngx-charts series tooltip behavior.
 */
export function lineChartOptions(config: LineChartConfig): ChartOptions<'line'> {
    return {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        interaction: {
            mode: 'index',
            intersect: false,
        },
        scales: {
            x: buildScale(config.xAxis, { isCategoryAxis: true }),
            y: buildScale(config.yAxis, { isCategoryAxis: false }),
        },
        plugins: {
            legend: buildLegend(config.legend),
            tooltip: buildTooltip(config.tooltip),
            datalabels: { display: false },
        },
    } as ChartOptions<'line'>;
}

/**
 * Builds chart.js options for pie/doughnut charts. The ngx-charts arcWidth (ring width as a
 * fraction of the radius) is mapped to the chart.js cutout percentage.
 */
export function doughnutChartOptions(config: DoughnutChartConfig): ChartOptions<'doughnut'> {
    const arcWidth = config.arcWidth ?? 0.25;
    return {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        cutout: `${Math.round((1 - arcWidth) * 100)}%`,
        radius: config.radius,
        plugins: {
            legend: buildLegend(config.legend),
            tooltip: buildTooltip(config.tooltip),
            datalabels: { display: false },
        },
    } as ChartOptions<'doughnut'>;
}
