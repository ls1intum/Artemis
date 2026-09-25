/** Configuration of a single chart axis. */
export interface TumAetUiChartAxisConfig {
    /** Axis title rendered next to the ticks. */
    label?: string;
    min?: number;
    max?: number;
    /** Formats tick labels; receives the category label (category axis) or the numeric tick value (value axis). */
    tickFormatter?: (value: number | string) => string;
    /** Hides the axis entirely (ticks, title and grid). Defaults to true. */
    display?: boolean;
}

/**
 * One series of a chart. For a single-series bar chart, `colors` assigns a distinct color per
 * category; for grouped or stacked bars, `color` colors the whole series.
 *
 * Colors may be plain CSS colors or `var(--token)` references — unlike a canvas renderer, SVG
 * consumes custom properties directly, so no resolution step is needed and theme switches apply
 * without re-rendering.
 */
export interface TumAetUiChartSeries {
    label?: string;
    data: readonly (number | undefined)[];
    color?: string;
    colors?: readonly string[];
    /** Arbitrary per-datum metadata, index-aligned with `data`, surfaced in tooltips and select events. */
    meta?: readonly unknown[];
    /**
     * Marks the series as a decorative marker such as an average line. It is drawn dashed and is
     * left out of the legend, the tooltip and select events.
     */
    referenceLine?: boolean;
}

/** A single hovered or clicked datum, passed to tooltip and data-label formatters. */
export interface TumAetUiChartDatumContext {
    seriesIndex: number;
    index: number;
    label: string;
    seriesLabel?: string;
    value: number;
    meta?: unknown;
}

export interface TumAetUiChartTooltipConfig {
    title?: (items: TumAetUiChartDatumContext[]) => string;
    label?: (item: TumAetUiChartDatumContext) => string | string[];
    /** Extra lines appended below the per-series lines, e.g. a shared note about the hovered category. */
    afterBody?: (items: TumAetUiChartDatumContext[]) => string | string[];
}

export type TumAetUiChartLegendPosition = 'top' | 'right' | 'bottom' | 'left';

export type TumAetUiChartLegendConfig = boolean | { position?: TumAetUiChartLegendPosition };

export interface TumAetUiBarChartConfig {
    /** Renders horizontal bars: categories run down the y axis, values along the x axis. */
    horizontal?: boolean;
    /** Stacks series on top of each other instead of grouping them side by side. */
    stacked?: boolean;
    /** Treats the value axis as a percentage: ticks get a '%' suffix and the axis is capped at 100. */
    percentScale?: boolean;
    /** Caps a bar's cross-axis thickness in px, for slim summary bars that should not fill the container. */
    maxBarThickness?: number;
    xAxis?: TumAetUiChartAxisConfig;
    yAxis?: TumAetUiChartAxisConfig;
    /** Defaults to hidden. */
    legend?: TumAetUiChartLegendConfig;
    /** `false` disables tooltips; omitting it renders the default `label: value` tooltip. */
    tooltip?: false | TumAetUiChartTooltipConfig;
    /** Persistent labels drawn at the end of each bar. */
    dataLabels?: { formatter: (value: number, context: TumAetUiChartDatumContext) => string };
}

export interface TumAetUiChartSelectEvent {
    seriesIndex: number;
    index: number;
    label?: string;
    seriesLabel?: string;
    value?: number;
    meta?: unknown;
}

export interface TumAetUiLineChartConfig {
    xAxis?: TumAetUiChartAxisConfig;
    yAxis?: TumAetUiChartAxisConfig;
    /** Defaults to hidden. */
    legend?: TumAetUiChartLegendConfig;
    /** `false` disables tooltips; omitting it renders the default `series: value` tooltip. */
    tooltip?: false | TumAetUiChartTooltipConfig;
    /** Monotone cubic interpolation instead of straight segments; never overshoots a data point. */
    monotone?: boolean;
    /** Draws straight across missing values instead of leaving a gap in the line. */
    spanGaps?: boolean;
    /** Draws a marker at every data point. Defaults to true. */
    points?: boolean;
}

export interface TumAetUiDoughnutChartConfig {
    /** Width of the ring as a fraction of the radius. Defaults to 0.25; pass 1 for a full pie. */
    arcWidth?: number;
    /** Inset around the arc in px. Defaults to 20. */
    padding?: number;
    /** Defaults to hidden. */
    legend?: TumAetUiChartLegendConfig;
    tooltip?: false | TumAetUiChartTooltipConfig;
}
