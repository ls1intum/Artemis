import { TumUiChartAxisConfig, TumUiChartLegendConfig, TumUiChartLegendPosition } from './tum-ui-chart.types';
import { BandScale, LinearScale, approximateTextWidth } from './tum-ui-chart.scales';

export const TICK_FONT_SIZE = 11;
export const AXIS_TITLE_FONT_SIZE = 12;
export const TICK_GAP = 6;
export const EDGE_PADDING = 8;
export const CATEGORY_PADDING = 0.25;
/** Room reserved beyond the end of a bar for its data label. */
export const DATA_LABEL_GAP = 4;

export interface ChartMargin {
    top: number;
    right: number;
    bottom: number;
    left: number;
}

/** The drawing area inside the axis margins. All series coordinates are relative to its origin. */
export interface ChartPlot {
    width: number;
    height: number;
    left: number;
    top: number;
}

export interface ChartTick {
    key: string;
    text: string;
    x: number;
    y: number;
    anchor: string;
    /** Rotation in degrees around (x, y); 0 for upright labels. */
    rotate: number;
}

export interface ChartGridLine {
    key: string;
    x1: number;
    y1: number;
    x2: number;
    y2: number;
}

export interface ChartAxisTitle {
    text: string;
    x: number;
    y: number;
    rotate: number;
}

export interface ChartLegendItem {
    key: string;
    label: string;
    color: string;
}

export interface ValueTick {
    value: number;
    text: string;
}

export interface CartesianFrameInput {
    size: { width: number; height: number };
    labels: readonly string[];
    valueTicks: readonly ValueTick[];
    /** Categories run down the y axis and values along the x axis. */
    horizontal: boolean;
    valueAxis?: TumUiChartAxisConfig;
    categoryAxis?: TumUiChartAxisConfig;
    xAxisTitle?: string;
    yAxisTitle?: string;
    /** Additional room at the end of the value axis, e.g. for data labels. */
    valueEndPadding?: number;
}

export interface CartesianFrame {
    margin: ChartMargin;
    plot: ChartPlot;
    /**
     * Category labels on a vertical chart are rotated once they can no longer sit side by side.
     * Rotating all of them keeps the axis legible without dropping any label, which matters here
     * because the labels carry meaning (grade buckets, exercise titles) rather than a continuum.
     */
    rotateCategoryLabels: boolean;
}

function titleAllowance(title?: string): number {
    return title ? AXIS_TITLE_FONT_SIZE + 6 : 0;
}

export function cartesianFrame(input: CartesianFrameInput): CartesianFrame {
    const valueAxisVisible = input.valueAxis?.display ?? true;
    const categoryAxisVisible = input.categoryAxis?.display ?? true;
    const valueTickWidth = valueAxisVisible ? Math.max(...input.valueTicks.map((tick) => approximateTextWidth(tick.text, TICK_FONT_SIZE)), 0) : 0;
    const categoryLabelWidth = categoryAxisVisible ? Math.max(...input.labels.map((label) => approximateTextWidth(label, TICK_FONT_SIZE)), 0) : 0;
    const endPadding = input.valueEndPadding ?? 0;

    let margin: ChartMargin;
    let rotateCategoryLabels = false;

    if (input.horizontal) {
        margin = {
            top: EDGE_PADDING,
            right: EDGE_PADDING + endPadding,
            bottom: (valueAxisVisible ? TICK_FONT_SIZE + TICK_GAP : 0) + titleAllowance(input.xAxisTitle),
            left: categoryLabelWidth + TICK_GAP + titleAllowance(input.yAxisTitle),
        };
    } else {
        const available = Math.max(input.size.width - valueTickWidth - TICK_GAP - EDGE_PADDING, 1);
        const required = input.labels.reduce((sum, label) => sum + approximateTextWidth(label, TICK_FONT_SIZE) + 8, 0);
        rotateCategoryLabels = categoryAxisVisible && required > available;
        const rotatedHeight = Math.min(categoryLabelWidth * 0.72, 70);
        const categoryBandHeight = categoryAxisVisible ? (rotateCategoryLabels ? rotatedHeight : TICK_FONT_SIZE) + TICK_GAP : 0;
        margin = {
            top: EDGE_PADDING + endPadding,
            // Rotated labels lean to the left of their tick, so the leftmost one needs room to sit in.
            right: EDGE_PADDING,
            bottom: categoryBandHeight + titleAllowance(input.xAxisTitle),
            left: Math.max(valueTickWidth + TICK_GAP, rotateCategoryLabels ? rotatedHeight * 0.7 : 0) + titleAllowance(input.yAxisTitle),
        };
    }

    return {
        margin,
        plot: {
            width: Math.max(input.size.width - margin.left - margin.right, 0),
            height: Math.max(input.size.height - margin.top - margin.bottom, 0),
            left: margin.left,
            top: margin.top,
        },
        rotateCategoryLabels,
    };
}

export function valueTickViews(plot: ChartPlot, scale: LinearScale, ticks: readonly ValueTick[], horizontal: boolean): ChartTick[] {
    return ticks.map((tick) =>
        horizontal
            ? { key: `v${tick.value}`, text: tick.text, x: scale(tick.value), y: plot.height + TICK_GAP + TICK_FONT_SIZE * 0.8, anchor: 'middle', rotate: 0 }
            : { key: `v${tick.value}`, text: tick.text, x: -TICK_GAP, y: scale(tick.value) + TICK_FONT_SIZE * 0.35, anchor: 'end', rotate: 0 },
    );
}

export function categoryTickViews(
    plot: ChartPlot,
    categories: BandScale,
    labels: readonly string[],
    horizontal: boolean,
    rotate: boolean,
    formatter?: (value: number | string) => string,
): ChartTick[] {
    return labels.map((label, index) => {
        const center = categories.center(label) ?? 0;
        const text = formatter ? formatter(label) : label;
        if (horizontal) {
            return { key: `c${index}`, text, x: -TICK_GAP, y: center + TICK_FONT_SIZE * 0.35, anchor: 'end', rotate: 0 };
        }
        return {
            key: `c${index}`,
            text,
            x: center,
            y: plot.height + TICK_GAP + (rotate ? TICK_FONT_SIZE * 0.4 : TICK_FONT_SIZE * 0.8),
            anchor: rotate ? 'end' : 'middle',
            rotate: rotate ? -45 : 0,
        };
    });
}

export function gridLineViews(plot: ChartPlot, scale: LinearScale, ticks: readonly ValueTick[], horizontal: boolean): ChartGridLine[] {
    return ticks.map((tick) => {
        const at = scale(tick.value);
        return horizontal ? { key: `g${tick.value}`, x1: at, y1: 0, x2: at, y2: plot.height } : { key: `g${tick.value}`, x1: 0, y1: at, x2: plot.width, y2: at };
    });
}

export function axisTitleViews(plot: ChartPlot, margin: ChartMargin, xTitle?: string, yTitle?: string): { x?: ChartAxisTitle; y?: ChartAxisTitle } {
    return {
        x: xTitle ? { text: xTitle, x: plot.width / 2, y: plot.height + margin.bottom - 2, rotate: 0 } : undefined,
        y: yTitle ? { text: yTitle, x: -(margin.left - AXIS_TITLE_FONT_SIZE), y: plot.height / 2, rotate: -90 } : undefined,
    };
}

export function legendPositionOf(legend: TumUiChartLegendConfig | undefined): TumUiChartLegendPosition | undefined {
    if (!legend) {
        return undefined;
    }
    return typeof legend === 'object' ? (legend.position ?? 'right') : 'right';
}
