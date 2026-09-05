import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, afterNextRender, booleanAttribute, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { TumUiBarChartConfig, TumUiChartAxisConfig, TumUiChartDatumContext, TumUiChartSelectEvent, TumUiChartSeries } from './tum-ui-chart.types';
import { allIntegers, bandScale, linearScale, niceDomain } from './tum-ui-chart.scales';
import {
    CATEGORY_PADDING,
    ChartAxisTitle,
    ChartGridLine,
    ChartLegendItem,
    ChartTick,
    DATA_LABEL_GAP,
    TICK_FONT_SIZE,
    ValueTick,
    axisTitleViews,
    cartesianFrame,
    categoryTickViews,
    gridLineViews,
    legendPositionOf,
    valueTickViews,
} from './tum-ui-chart.frame';
import { TumUiChartAxesComponent } from './tum-ui-chart-axes.component';
import { TumUiChartLegendComponent } from './tum-ui-chart-legend.component';
import { TumUiChartTooltipComponent } from './tum-ui-chart-tooltip.component';
import { TumUiChartDataTableComponent } from './tum-ui-chart-data-table.component';

const SERIES_GROUP_PADDING = 0.08;

interface BarView {
    key: string;
    x: number;
    y: number;
    width: number;
    height: number;
    color: string;
    context: TumUiChartDatumContext;
    dataLabel?: { x: number; y: number; anchor: string; text: string };
}

/**
 * A bar chart rendered as inline SVG.
 *
 * Because every label is a real `<text>` node, chart text is selectable and copyable, is exposed to
 * assistive technology, and takes its colors from CSS custom properties — so theme switches need no
 * re-render and no color resolution step.
 */
@Component({
    selector: 'tum-ui-bar-chart',
    templateUrl: './tum-ui-bar-chart.component.html',
    styleUrl: './tum-ui-bar-chart.component.scss',
    imports: [TumUiChartAxesComponent, TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'tum-ui-bar-chart' },
})
export class TumUiBarChartComponent implements OnDestroy {
    private readonly hostElement = inject<ElementRef<HTMLElement>>(ElementRef);
    /**
     * The plot is measured on the SVG itself rather than on the host, because a legend is a sibling
     * flex item: measuring the host would size the plot as if the legend's band were still free and
     * paint the axis labels underneath it.
     */
    private readonly canvas = viewChild.required<ElementRef<SVGSVGElement>>('canvas');

    readonly labels = input.required<readonly string[]>();
    readonly series = input.required<readonly TumUiChartSeries[]>();
    readonly config = input<TumUiBarChartConfig>({});
    readonly ariaLabel = input<string>();

    /** Marks bars as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    readonly interactive = input(false, { transform: booleanAttribute });

    readonly dataSelect = output<TumUiChartSelectEvent>();

    private readonly size = signal({ width: 0, height: 0 });
    protected readonly hovered = signal<{ index: number; seriesIndex: number; x: number; y: number } | undefined>(undefined);
    private resizeObserver?: ResizeObserver;

    constructor() {
        afterNextRender(() => {
            const element = this.canvas().nativeElement;
            const rect = element.getBoundingClientRect();
            this.size.set({ width: rect.width, height: rect.height });
            if (typeof ResizeObserver === 'undefined') {
                return;
            }
            this.resizeObserver = new ResizeObserver((entries) => {
                const box = entries[0]?.contentRect;
                if (box) {
                    this.size.set({ width: box.width, height: box.height });
                }
            });
            this.resizeObserver.observe(element);
        });
    }

    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
    }

    private readonly horizontal = computed(() => this.config().horizontal ?? false);
    private readonly stacked = computed(() => this.config().stacked ?? false);

    /** The axis carrying the numeric values: x for horizontal bars, y otherwise. */
    private readonly valueAxis = computed<TumUiChartAxisConfig | undefined>(() => (this.horizontal() ? this.config().xAxis : this.config().yAxis));
    private readonly categoryAxis = computed<TumUiChartAxisConfig | undefined>(() => (this.horizontal() ? this.config().yAxis : this.config().xAxis));

    /**
     * Whole numbers on the value axis mean the axis must not step in fractions: a "number of
     * submissions" axis running 0–3 would otherwise be labelled 0, 1, 1, 2, 2, 3 once the caller's
     * integer formatter collapsed the half steps.
     */
    private readonly minTickStep = computed(() => (allIntegers(this.series().flatMap((entry) => [...entry.data])) ? 1 : 0));

    private readonly valueDomain = computed<[number, number]>(() => {
        const axis = this.valueAxis();
        const percent = this.config().percentScale ?? false;
        const totals = this.stacked()
            ? this.labels().map((_, index) => this.series().reduce((sum, entry) => sum + (entry.data[index] ?? 0), 0))
            : this.series().flatMap((entry) => entry.data.map((value) => value ?? 0));
        const dataMax = totals.length ? Math.max(...totals) : 0;
        const dataMin = totals.length ? Math.min(...totals) : 0;
        const [niceMin, niceMax] = niceDomain(Math.min(0, dataMin), dataMax, 5, this.minTickStep());
        return [axis?.min ?? niceMin, axis?.max ?? (percent ? 100 : niceMax)];
    });

    private readonly valueTickLabels = computed<ValueTick[]>(() => {
        if ((this.valueAxis()?.display ?? true) === false) {
            return [];
        }
        const [min, max] = this.valueDomain();
        const percent = this.config().percentScale ?? false;
        const format = this.valueAxis()?.tickFormatter ?? (percent ? (value: number | string) => `${value}%` : undefined);
        return linearScale([min, max], [0, 1])
            .ticks(5, this.minTickStep())
            .map((value) => ({ value, text: format ? format(value) : `${value}` }));
    });

    private readonly frame = computed(() =>
        cartesianFrame({
            size: this.size(),
            labels: (this.categoryAxis()?.display ?? true) ? this.labels() : [],
            valueTicks: this.valueTickLabels(),
            horizontal: this.horizontal(),
            valueAxis: this.valueAxis(),
            categoryAxis: this.categoryAxis(),
            xAxisTitle: this.config().xAxis?.label,
            yAxisTitle: this.config().yAxis?.label,
            valueEndPadding: this.config().dataLabels ? TICK_FONT_SIZE + DATA_LABEL_GAP : 0,
        }),
    );

    protected readonly plot = computed(() => this.frame().plot);

    private readonly valueScale = computed(() => {
        const plot = this.plot();
        const [min, max] = this.valueDomain();
        return this.horizontal() ? linearScale([min, max], [0, plot.width]) : linearScale([min, max], [plot.height, 0]);
    });

    private readonly categoryScale = computed(() => {
        const plot = this.plot();
        return bandScale(this.labels(), this.horizontal() ? plot.height : plot.width, CATEGORY_PADDING);
    });

    protected readonly bars = computed<BarView[]>(() => {
        const plot = this.plot();
        if (plot.width <= 0 || plot.height <= 0) {
            return [];
        }
        const horizontal = this.horizontal();
        const stacked = this.stacked();
        const labels = this.labels();
        const series = this.series();
        const [min, max] = this.valueDomain();
        const categories = this.categoryScale();
        const valueScale = this.valueScale();

        const grouped = !stacked && series.length > 1;
        const groupScale = grouped
            ? bandScale(
                  series.map((_, index) => `${index}`),
                  categories.bandwidth,
                  SERIES_GROUP_PADDING,
              )
            : undefined;
        const maxThickness = this.config().maxBarThickness ?? Number.POSITIVE_INFINITY;
        const thickness = Math.min(groupScale ? groupScale.bandwidth : categories.bandwidth, maxThickness);
        const dataLabels = this.config().dataLabels;

        const stackOffsets = labels.map(() => 0);
        const bars: BarView[] = [];

        series.forEach((entry, seriesIndex) => {
            labels.forEach((label, index) => {
                const raw = entry.data[index];
                if (raw === undefined || raw === null) {
                    return;
                }
                const bandStart = categories.position(label);
                if (bandStart === undefined) {
                    return;
                }
                const groupOffset = groupScale ? (groupScale.position(`${seriesIndex}`) ?? 0) : 0;
                const centering = (groupScale ? groupScale.bandwidth : categories.bandwidth) - thickness;
                const crossStart = bandStart + groupOffset + centering / 2;

                const start = stacked ? stackOffsets[index] : Math.min(Math.max(0, min), max);
                const end = stacked ? stackOffsets[index] + raw : raw;
                if (stacked) {
                    stackOffsets[index] = end;
                }
                const from = valueScale(start);
                const to = valueScale(end);

                const context: TumUiChartDatumContext = {
                    seriesIndex,
                    index,
                    label,
                    seriesLabel: entry.label,
                    value: raw,
                    meta: entry.meta?.[index],
                };
                const color = entry.colors?.[index % entry.colors.length] ?? entry.color ?? 'var(--tumaet-ui-primary-color)';

                const bar: BarView = horizontal
                    ? { key: `${seriesIndex}-${index}`, x: Math.min(from, to), y: crossStart, width: Math.abs(to - from), height: thickness, color, context }
                    : { key: `${seriesIndex}-${index}`, x: crossStart, y: Math.min(from, to), width: thickness, height: Math.abs(to - from), color, context };

                if (dataLabels) {
                    const text = dataLabels.formatter(raw, context);
                    bar.dataLabel = horizontal
                        ? { x: bar.x + bar.width + DATA_LABEL_GAP, y: bar.y + bar.height / 2, anchor: 'start', text }
                        : { x: bar.x + bar.width / 2, y: bar.y - DATA_LABEL_GAP, anchor: 'middle', text };
                }
                bars.push(bar);
            });
        });
        return bars;
    });

    protected readonly gridLines = computed<ChartGridLine[]>(() =>
        (this.valueAxis()?.display ?? true) ? gridLineViews(this.plot(), this.valueScale(), this.valueTickLabels(), this.horizontal()) : [],
    );

    protected readonly ticks = computed<ChartTick[]>(() => {
        const plot = this.plot();
        const horizontal = this.horizontal();
        const value = (this.valueAxis()?.display ?? true) ? valueTickViews(plot, this.valueScale(), this.valueTickLabels(), horizontal) : [];
        const category =
            (this.categoryAxis()?.display ?? true)
                ? categoryTickViews(plot, this.categoryScale(), this.labels(), horizontal, this.frame().rotateCategoryLabels, this.categoryAxis()?.tickFormatter)
                : [];
        return [...value, ...category];
    });

    protected readonly axisTitles = computed<ChartAxisTitle[]>(() => {
        const titles = axisTitleViews(this.plot(), this.frame().margin, this.config().xAxis?.label, this.config().yAxis?.label);
        return [titles.x, titles.y].filter((title): title is ChartAxisTitle => title !== undefined);
    });

    protected readonly legendPosition = computed(() => legendPositionOf(this.config().legend));

    protected readonly legendItems = computed<ChartLegendItem[]>(() =>
        this.series()
            .filter((entry) => entry.label)
            .map((entry, index) => ({ key: `${index}`, label: entry.label!, color: entry.color ?? 'var(--tumaet-ui-primary-color)' })),
    );

    protected readonly tooltip = computed(() => {
        const hovered = this.hovered();
        const config = this.config().tooltip;
        if (!hovered || config === false) {
            return undefined;
        }
        const bar = this.bars().find((candidate) => candidate.context.index === hovered.index && candidate.context.seriesIndex === hovered.seriesIndex);
        if (!bar) {
            return undefined;
        }
        const context = bar.context;
        const title = config?.title ? config.title([context]) : context.label;
        const raw = config?.label ? config.label(context) : `${context.seriesLabel ? `${context.seriesLabel}: ` : ''}${context.value}`;
        const after = config?.afterBody?.([context]);
        const lines = [...(Array.isArray(raw) ? raw : [raw]), ...(after ? (Array.isArray(after) ? after : [after]) : [])].filter((line) => line !== '');
        return { title, lines, x: hovered.x, y: hovered.y };
    });

    protected readonly accessibleRows = computed(() =>
        this.labels().map((label, index) => ({
            label,
            values: this.series().map((entry) => ({ seriesLabel: entry.label, value: entry.data[index] })),
        })),
    );

    protected onBarEnter(bar: BarView, event: MouseEvent): void {
        const host = this.hostElement.nativeElement.getBoundingClientRect();
        this.hovered.set({ index: bar.context.index, seriesIndex: bar.context.seriesIndex, x: event.clientX - host.left, y: event.clientY - host.top });
    }

    protected onBarLeave(): void {
        this.hovered.set(undefined);
    }

    protected onBarSelect(bar: BarView): void {
        const { seriesIndex, index, label, seriesLabel, value, meta } = bar.context;
        this.dataSelect.emit({ seriesIndex, index, label, seriesLabel, value, meta });
    }
}
