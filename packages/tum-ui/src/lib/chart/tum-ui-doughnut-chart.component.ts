import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, afterNextRender, booleanAttribute, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { TumUiChartDatumContext, TumUiChartSelectEvent, TumUiChartSeries, TumUiDoughnutChartConfig } from './tum-ui-chart.types';
import { arcPath, sliceAngles } from './tum-ui-chart.arcs';
import { ChartLegendItem, legendPositionOf, placeTooltip } from './tum-ui-chart.frame';
import { TumUiChartLegendComponent } from './tum-ui-chart-legend.component';
import { TumUiChartTooltipComponent } from './tum-ui-chart-tooltip.component';
import { TumUiChartDataTableComponent } from './tum-ui-chart-data-table.component';

const DEFAULT_ARC_WIDTH = 0.25;
const DEFAULT_PADDING = 20;

interface SliceView {
    key: string;
    path: string;
    color: string;
    context: TumUiChartDatumContext;
}

/**
 * A doughnut chart rendered as inline SVG, drawn from the first series. An `arcWidth` of 1 fills the
 * ring completely and produces a pie chart.
 */
@Component({
    selector: 'tum-ui-doughnut-chart',
    templateUrl: './tum-ui-doughnut-chart.component.html',
    styleUrl: './tum-ui-doughnut-chart.component.scss',
    imports: [TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { class: 'tum-ui-doughnut-chart' },
})
export class TumUiDoughnutChartComponent implements OnDestroy {
    private readonly hostElement = inject<ElementRef<HTMLElement>>(ElementRef);
    private readonly canvas = viewChild.required<ElementRef<SVGSVGElement>>('canvas');

    readonly labels = input.required<readonly string[]>();
    readonly series = input.required<readonly TumUiChartSeries[]>();
    readonly config = input<TumUiDoughnutChartConfig>({});
    readonly ariaLabel = input<string>();

    /** Names the chart from a visible heading instead of a literal label. */
    readonly ariaLabelledBy = input<string>();

    /** Marks slices as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    readonly interactive = input(false, { transform: booleanAttribute });

    readonly dataSelect = output<TumUiChartSelectEvent>();

    private readonly size = signal({ width: 0, height: 0 });
    protected readonly hovered = signal<{ index: number; x: number; y: number; hostWidth: number; hostHeight: number } | undefined>(undefined);
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

    /** Slices the reader switched off in the legend, by index. */
    private readonly hiddenSlices = signal<ReadonlySet<string>>(new Set());

    protected onLegendToggle(key: string): void {
        this.hiddenSlices.update((hidden) => {
            const next = new Set(hidden);
            if (!next.delete(key)) {
                next.add(key);
            }
            return next;
        });
    }

    private readonly primarySeries = computed<TumUiChartSeries | undefined>(() => this.series()[0]);

    protected readonly slices = computed<SliceView[]>(() => {
        const { width, height } = this.size();
        const series = this.primarySeries();
        if (!series || width <= 0 || height <= 0) {
            return [];
        }
        const padding = this.config().padding ?? DEFAULT_PADDING;
        const outerRadius = Math.max(Math.min(width, height) / 2 - padding, 0);
        const arcWidth = this.config().arcWidth ?? DEFAULT_ARC_WIDTH;
        const innerRadius = outerRadius * (1 - Math.min(Math.max(arcWidth, 0), 1));
        const centerX = width / 2;
        const centerY = height / 2;

        // A hidden slice contributes nothing, so the remaining slices grow to fill the ring. A negative
        // value cannot be expressed as a share of a circle either, so it too is drawn as no arc at all;
        // the tooltip and the data table still report what the caller passed rather than hiding it.
        const values = series.data.map((value, index) => (this.hiddenSlices().has(`${index}`) ? 0 : (value ?? 0)));
        return sliceAngles(values).map((slice, index) => ({
            key: `${index}`,
            path: arcPath(centerX, centerY, innerRadius, outerRadius, slice.startAngle, slice.endAngle),
            color: series.colors?.[index % series.colors.length] ?? series.color ?? 'var(--tumaet-ui-primary-color)',
            context: {
                seriesIndex: 0,
                index,
                label: this.labels()[index] ?? '',
                seriesLabel: series.label,
                value: values[index],
                meta: series.meta?.[index],
            },
        }));
    });

    protected readonly legendPosition = computed(() => legendPositionOf(this.config().legend));

    /** A doughnut's legend names the slices rather than the series, so it follows the categories. */
    protected readonly legendItems = computed<ChartLegendItem[]>(() =>
        this.slices().map((slice) => ({ key: slice.key, label: slice.context.label, color: slice.color, hidden: this.hiddenSlices().has(slice.key) })),
    );

    protected readonly tooltip = computed(() => {
        const hovered = this.hovered();
        const config = this.config().tooltip;
        if (!hovered || config === false) {
            return undefined;
        }
        const slice = this.slices()[hovered.index];
        if (!slice) {
            return undefined;
        }
        const context = slice.context;
        const title = config?.title ? config.title([context]) : context.label;
        const raw = config?.label ? config.label(context) : `${context.value}`;
        const after = config?.afterBody?.([context]);
        const lines = [...(Array.isArray(raw) ? raw : [raw]), ...(after ? (Array.isArray(after) ? after : [after]) : [])].filter((line) => line !== '');
        return { title, lines, ...placeTooltip(hovered) };
    });

    protected readonly accessibleRows = computed(() =>
        this.labels().map((label, index) => ({
            label,
            values: [{ seriesLabel: this.primarySeries()?.label, value: this.primarySeries()?.data[index] }],
        })),
    );

    protected onSliceEnter(slice: SliceView, event: MouseEvent): void {
        const host = this.hostElement.nativeElement.getBoundingClientRect();
        this.hovered.set({ index: slice.context.index, x: event.clientX - host.left, y: event.clientY - host.top, hostWidth: host.width, hostHeight: host.height });
    }

    protected onSliceLeave(): void {
        this.hovered.set(undefined);
    }

    protected onSliceSelect(slice: SliceView): void {
        const { seriesIndex, index, label, seriesLabel, value, meta } = slice.context;
        this.dataSelect.emit({ seriesIndex, index, label, seriesLabel, value, meta });
    }
}
