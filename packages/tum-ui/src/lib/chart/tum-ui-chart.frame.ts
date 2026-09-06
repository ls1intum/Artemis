import { TumUiChartAxisConfig, TumUiChartDatumContext, TumUiChartLegendConfig, TumUiChartLegendPosition } from './tum-ui-chart.types';
import { BandScale, LinearScale, approximateTextWidth } from './tum-ui-chart.scales';

export const TICK_FONT_SIZE = 11;
export const AXIS_TITLE_FONT_SIZE = 12;
export const TICK_GAP = 6;
export const EDGE_PADDING = 8;
export const CATEGORY_PADDING = 0.25;
/** Upper bound on the share of the chart a category axis may spend on its own labels. */
const MAX_CATEGORY_AXIS_SHARE = 0.33;

/** Vertical extent of a rotated label as a fraction of its own length, at the angle the axis draws them. */
const ROTATED_LABEL_PROJECTION = 0.72;
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
    /** The reader has switched this entry off, so its series or slice is left out of the chart. */
    hidden?: boolean;
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
    /** Room a single category label may occupy before it has to be truncated. */
    categoryLabelBudget: number;
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
    // Measure the text that is actually drawn, so an axis formatter that truncates is accounted for.
    const format = input.categoryAxis?.tickFormatter;
    const categoryLabelWidth = categoryAxisVisible ? Math.max(...input.labels.map((label) => approximateTextWidth(format ? format(label) : label, TICK_FONT_SIZE)), 0) : 0;
    const endPadding = input.valueEndPadding ?? 0;

    let margin: ChartMargin;
    let rotateCategoryLabels = false;

    // Reserved vertical band for rotated labels; also the budget a rotated label has to fit into.
    let rotatedHeight = 0;
    if (input.horizontal) {
        // A long category title must not eat the plot: past a third of the width the label is
        // truncated instead, which keeps the bars visible rather than collapsing them to nothing.
        const categoryAllowance = Math.min(categoryLabelWidth, input.size.width * MAX_CATEGORY_AXIS_SHARE);
        margin = {
            top: EDGE_PADDING,
            right: EDGE_PADDING + endPadding,
            bottom: (valueAxisVisible ? TICK_FONT_SIZE + TICK_GAP : 0) + titleAllowance(input.xAxisTitle),
            left: categoryAllowance + TICK_GAP + titleAllowance(input.yAxisTitle),
        };
    } else {
        const available = Math.max(input.size.width - valueTickWidth - TICK_GAP - EDGE_PADDING, 1);
        const required = input.labels.reduce((sum, label) => sum + approximateTextWidth(format ? format(label) : label, TICK_FONT_SIZE) + 8, 0);
        rotateCategoryLabels = categoryAxisVisible && required > available;
        rotatedHeight = Math.min(categoryLabelWidth * ROTATED_LABEL_PROJECTION, Math.max(input.size.height * MAX_CATEGORY_AXIS_SHARE, 0));
        const categoryBandHeight = categoryAxisVisible ? (rotateCategoryLabels ? rotatedHeight : TICK_FONT_SIZE) + TICK_GAP : 0;
        margin = {
            top: EDGE_PADDING + endPadding,
            right: EDGE_PADDING,
            bottom: categoryBandHeight + titleAllowance(input.xAxisTitle),
            // Rotated labels lean to the left of their tick, so the leftmost one needs room to sit in.
            left: Math.max(valueTickWidth + TICK_GAP, rotateCategoryLabels ? rotatedHeight * 0.7 : 0) + titleAllowance(input.yAxisTitle),
        };
    }

    const plotWidth = Math.max(input.size.width - margin.left - margin.right, 0);

    return {
        margin,
        plot: {
            width: Math.max(input.size.width - margin.left - margin.right, 0),
            height: Math.max(input.size.height - margin.top - margin.bottom, 0),
            left: margin.left,
            top: margin.top,
        },
        rotateCategoryLabels,
        // A vertical chart reserves only a slice of its height for category labels, so the label has to fit
        // that slice. Left unbounded, a long title is drawn in full and runs off the chart over whatever
        // follows it. Rotated labels are measured along their own direction, hence dividing by the projection.
        categoryLabelBudget: input.horizontal
            ? Math.max(margin.left - TICK_GAP - titleAllowance(input.yAxisTitle), 0)
            : rotateCategoryLabels
              ? Math.max(rotatedHeight / ROTATED_LABEL_PROJECTION, 0)
              : Math.max(plotWidth / Math.max(input.labels.length, 1) - TICK_GAP, 0),
    };
}

/** Shortens a label to the pixels available for it, so it cannot spill over the rest of the page. */
export function truncateToWidth(text: string, budget: number): string {
    if (!Number.isFinite(budget) || approximateTextWidth(text, TICK_FONT_SIZE) <= budget) {
        return text;
    }
    const perCharacter = approximateTextWidth('n', TICK_FONT_SIZE);
    const fits = Math.max(Math.floor(budget / perCharacter) - 1, 1);
    return `${text.slice(0, fits)}…`;
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
    labelBudget = Number.POSITIVE_INFINITY,
): ChartTick[] {
    const skip = categoryTickSkip(plot, labels, horizontal, rotate, formatter);
    return labels.flatMap((label, index) => {
        if (index % skip !== 0) {
            return [];
        }
        const center = categories.center(index);
        const text = truncateToWidth(formatter ? formatter(label) : label, labelBudget);
        if (horizontal) {
            return [{ key: `c${index}`, text, x: -TICK_GAP, y: center + TICK_FONT_SIZE * 0.35, anchor: 'end', rotate: 0 }];
        }
        return [
            {
                key: `c${index}`,
                text,
                x: center,
                y: plot.height + TICK_GAP + (rotate ? TICK_FONT_SIZE * 0.4 : TICK_FONT_SIZE * 0.8),
                anchor: rotate ? 'end' : 'middle',
                rotate: rotate ? -45 : 0,
            },
        ];
    });
}

/**
 * How many categories to advance between rendered labels. Rotating buys roughly three times the room
 * of upright text; beyond that, drawing every label would only overprint them into a smear.
 */
function categoryTickSkip(plot: ChartPlot, labels: readonly string[], horizontal: boolean, rotate: boolean, formatter?: (value: number | string) => string): number {
    const available = horizontal ? plot.height : plot.width;
    if (!labels.length || available <= 0) {
        return 1;
    }
    const perLabel = horizontal ? TICK_FONT_SIZE + 4 : Math.max(...labels.map((label) => approximateTextWidth(formatter ? formatter(label) : label, TICK_FONT_SIZE)), 1) + 8;
    const required = labels.length * (rotate ? perLabel / 3 : perLabel);
    return Math.max(Math.ceil(required / available), 1);
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

/** Where a tooltip should sit, once kept inside the chart's own box. */
export interface TooltipPlacement {
    x: number;
    y: number;
    below: boolean;
}

/**
 * Half the width a tooltip is assumed to occupy. The real width depends on its text, so this is an
 * approximation: it only has to stop a tooltip near an edge from escaping a container that clips it.
 */
const ASSUMED_TOOLTIP_HALF_WIDTH = 110;
/** Room a tooltip needs above the pointer before it has to flip below instead. */
const TOOLTIP_CLEARANCE = 90;

export function placeTooltip(hovered: { x: number; y: number; hostWidth: number; hostHeight: number }): TooltipPlacement {
    const min = Math.min(ASSUMED_TOOLTIP_HALF_WIDTH + EDGE_PADDING, hovered.hostWidth / 2);
    const max = Math.max(hovered.hostWidth - ASSUMED_TOOLTIP_HALF_WIDTH - EDGE_PADDING, min);
    return { x: Math.min(Math.max(hovered.x, min), max), y: hovered.y, below: hovered.y < TOOLTIP_CLEARANCE };
}

/**
 * The accessible name of a single interactive datum. Where a chart draws more than one series, two
 * data points can share a category and a value, which would leave a keyboard or screen reader user
 * unable to tell which one they are about to select, so the series label leads in that case.
 */
export function datumAccessibleName(context: TumUiChartDatumContext, multiSeries: boolean): string {
    const series = multiSeries && context.seriesLabel ? `${context.seriesLabel}, ` : '';
    return `${series}${context.label}: ${context.value}`;
}
