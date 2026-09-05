import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, afterNextRender, booleanAttribute, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { TumUiChartDatumContext, TumUiChartSelectEvent, TumUiChartSeries, TumUiLineChartConfig } from './tum-ui-chart.types';
import { allIntegers, bandScale, linearScale, niceDomain } from './tum-ui-chart.scales';
import { CurvePoint, linearPath, monotoneCubicPath, segmentsOf } from './tum-ui-chart.curves';
import {
    ChartAxisTitle,
    ChartGridLine,
    ChartLegendItem,
    ChartTick,
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

const POINT_RADIUS = 3;
/** Lines are drawn at the band centers, so the band needs no gap between neighbours. */
const LINE_CATEGORY_PADDING = 0;

interface LineView {
    key: string;
    paths: string[];
    color: string;
    dashed: boolean;
    points: { key: string; x: number; y: number; context: TumUiChartDatumContext }[];
}

/**
 * A line chart rendered as inline SVG, with one line per series.
 *
 * Hovering reports every series at the hovered category at once, which is what makes several lines
 * comparable at a glance; a series marked as a reference line is drawn dashed and stays out of the
 * legend, the tooltip and select events.
 */
@Component({
    selector: 'tum-ui-line-chart',
    templateUrl: './tum-ui-line-chart.component.html',
    styleUrl: './tum-ui-line-chart.component.scss',
    imports: [TumUiChartAxesComponent, TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'tum-ui-line-chart' },
})
export class TumUiLineChartComponent implements OnDestroy {
    private readonly hostElement = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly canvas = viewChild.required<ElementRef<SVGSVGElement>>('canvas');

    readonly labels = input.required<readonly string[]>();
    readonly series = input.required<readonly TumUiChartSeries[]>();
    readonly config = input<TumUiLineChartConfig>({});
    readonly ariaLabel = input<string>();

    /** Marks points as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    readonly interactive = input(false, { transform: booleanAttribute });

    readonly dataSelect = output<TumUiChartSelectEvent>();

    protected readonly pointRadius = POINT_RADIUS;

    private readonly size = signal({ width: 0, height: 0 });
    protected readonly hovered = signal<{ index: number; x: number; y: number } | undefined>(undefined);
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

    /** As for the bar chart: an integer-valued series must not be given fractional ticks. */
    private readonly minTickStep = computed(() => (allIntegers(this.series().flatMap((entry) => [...entry.data])) ? 1 : 0));

    private readonly valueDomain = computed<[number, number]>(() => {
        const axis = this.config().yAxis;
        const values = this.series().flatMap((entry) => entry.data.filter((value): value is number => value !== undefined && value !== null));
        const dataMax = values.length ? Math.max(...values) : 0;
        const dataMin = values.length ? Math.min(...values) : 0;
        const [niceMin, niceMax] = niceDomain(dataMin, dataMax, 5, this.minTickStep());
        return [axis?.min ?? niceMin, axis?.max ?? niceMax];
    });

    private readonly valueTickLabels = computed<ValueTick[]>(() => {
        if ((this.config().yAxis?.display ?? true) === false) {
            return [];
        }
        const [min, max] = this.valueDomain();
        const format = this.config().yAxis?.tickFormatter;
        return linearScale([min, max], [0, 1])
            .ticks(5, this.minTickStep())
            .map((value) => ({ value, text: format ? format(value) : `${value}` }));
    });

    private readonly frame = computed(() =>
        cartesianFrame({
            size: this.size(),
            labels: (this.config().xAxis?.display ?? true) ? this.labels() : [],
            valueTicks: this.valueTickLabels(),
            horizontal: false,
            valueAxis: this.config().yAxis,
            categoryAxis: this.config().xAxis,
            xAxisTitle: this.config().xAxis?.label,
            yAxisTitle: this.config().yAxis?.label,
        }),
    );

    protected readonly plot = computed(() => this.frame().plot);

    private readonly valueScale = computed(() => {
        const [min, max] = this.valueDomain();
        return linearScale([min, max], [this.plot().height, 0]);
    });

    private readonly categoryScale = computed(() => bandScale(this.labels(), this.plot().width, LINE_CATEGORY_PADDING));

    protected readonly lines = computed<LineView[]>(() => {
        const plot = this.plot();
        if (plot.width <= 0 || plot.height <= 0) {
            return [];
        }
        const categories = this.categoryScale();
        const valueScale = this.valueScale();
        const monotone = this.config().monotone ?? false;
        const spanGaps = this.config().spanGaps ?? false;
        const showPoints = this.config().points ?? true;

        return this.series().map((entry, seriesIndex) => {
            const positioned = this.labels().map<CurvePoint | undefined>((label, index) => {
                const value = entry.data[index];
                if (value === undefined || value === null) {
                    return undefined;
                }
                return { x: categories.center(label) ?? 0, y: valueScale(value) };
            });
            const build = monotone ? monotoneCubicPath : linearPath;
            const paths = segmentsOf(positioned, spanGaps).map(build);
            const color = entry.color ?? 'var(--tumaet-ui-primary-color)';
            return {
                key: `${seriesIndex}`,
                paths,
                color,
                dashed: entry.referenceLine ?? false,
                points:
                    showPoints && !entry.referenceLine
                        ? positioned.flatMap((point, index) =>
                              point
                                  ? [
                                        {
                                            key: `${seriesIndex}-${index}`,
                                            x: point.x,
                                            y: point.y,
                                            context: {
                                                seriesIndex,
                                                index,
                                                label: this.labels()[index],
                                                seriesLabel: entry.label,
                                                value: entry.data[index]!,
                                                meta: entry.meta?.[index],
                                            },
                                        },
                                    ]
                                  : [],
                          )
                        : [],
            };
        });
    });

    protected readonly gridLines = computed<ChartGridLine[]>(() =>
        (this.config().yAxis?.display ?? true) ? gridLineViews(this.plot(), this.valueScale(), this.valueTickLabels(), false) : [],
    );

    protected readonly ticks = computed<ChartTick[]>(() => {
        const plot = this.plot();
        const value = (this.config().yAxis?.display ?? true) ? valueTickViews(plot, this.valueScale(), this.valueTickLabels(), false) : [];
        const category =
            (this.config().xAxis?.display ?? true)
                ? categoryTickViews(plot, this.categoryScale(), this.labels(), false, this.frame().rotateCategoryLabels, this.config().xAxis?.tickFormatter)
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
            .map((entry, index) => ({ entry, index }))
            .filter(({ entry }) => entry.label && !entry.referenceLine)
            .map(({ entry, index }) => ({ key: `${index}`, label: entry.label!, color: entry.color ?? 'var(--tumaet-ui-primary-color)' })),
    );

    /** The x coordinate of the guide drawn through the hovered category. */
    protected readonly guideX = computed(() => {
        const hovered = this.hovered();
        return hovered === undefined ? undefined : (this.categoryScale().center(this.labels()[hovered.index]) ?? undefined);
    });

    protected readonly tooltip = computed(() => {
        const hovered = this.hovered();
        const config = this.config().tooltip;
        if (!hovered || config === false) {
            return undefined;
        }
        const contexts: TumUiChartDatumContext[] = [];
        this.series().forEach((entry, seriesIndex) => {
            const value = entry.data[hovered.index];
            if (entry.referenceLine || value === undefined || value === null) {
                return;
            }
            contexts.push({
                seriesIndex,
                index: hovered.index,
                label: this.labels()[hovered.index],
                seriesLabel: entry.label,
                value,
                meta: entry.meta?.[hovered.index],
            });
        });
        if (!contexts.length) {
            return undefined;
        }
        const title = config?.title ? config.title(contexts) : this.labels()[hovered.index];
        const lines = contexts.flatMap((context) => {
            const raw = config?.label ? config.label(context) : `${context.seriesLabel ? `${context.seriesLabel}: ` : ''}${context.value}`;
            return Array.isArray(raw) ? raw : [raw];
        });
        const after = config?.afterBody?.(contexts);
        return {
            title,
            lines: [...lines, ...(after ? (Array.isArray(after) ? after : [after]) : [])].filter((line) => line !== ''),
            x: hovered.x,
            y: hovered.y,
        };
    });

    protected readonly accessibleRows = computed(() =>
        this.labels().map((label, index) => ({
            label,
            values: this.series().map((entry) => ({ seriesLabel: entry.label, value: entry.data[index] })),
        })),
    );

    /** Resolves a pointer position to the nearest category, so hovering anywhere reports a series. */
    protected onPlotMove(event: MouseEvent): void {
        const labels = this.labels();
        if (!labels.length) {
            return;
        }
        const canvas = this.canvas().nativeElement.getBoundingClientRect();
        const host = this.hostElement.nativeElement.getBoundingClientRect();
        const withinPlot = event.clientX - canvas.left - this.plot().left;
        const categories = this.categoryScale();
        let nearest = 0;
        let shortest = Number.POSITIVE_INFINITY;
        labels.forEach((label, index) => {
            const distance = Math.abs((categories.center(label) ?? 0) - withinPlot);
            if (distance < shortest) {
                shortest = distance;
                nearest = index;
            }
        });
        this.hovered.set({ index: nearest, x: event.clientX - host.left, y: event.clientY - host.top });
    }

    protected onPlotLeave(): void {
        this.hovered.set(undefined);
    }

    /**
     * Emits the point closest to the click. The hit area covers the plot so that the whole chart is
     * clickable rather than only the few pixels of a marker, which matches how the hover behaves.
     */
    protected onPlotClick(event: MouseEvent): void {
        const hovered = this.hovered();
        if (!hovered) {
            return;
        }
        const canvas = this.canvas().nativeElement.getBoundingClientRect();
        const withinPlot = event.clientY - canvas.top - this.plot().top;
        let nearest: TumUiChartDatumContext | undefined;
        let shortest = Number.POSITIVE_INFINITY;
        for (const line of this.lines()) {
            for (const point of line.points) {
                if (point.context.index !== hovered.index) {
                    continue;
                }
                const distance = Math.abs(point.y - withinPlot);
                if (distance < shortest) {
                    shortest = distance;
                    nearest = point.context;
                }
            }
        }
        if (!nearest) {
            return;
        }
        const { seriesIndex, index, label, seriesLabel, value, meta } = nearest;
        this.dataSelect.emit({ seriesIndex, index, label, seriesLabel, value, meta });
    }
}
