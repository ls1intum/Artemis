import { DestroyRef, ElementRef, Signal, afterNextRender, computed, inject, signal } from '@angular/core';
import { TumAetUiChartSeries } from './tumaet-ui-chart.types';
import { TumAetUiChartDataTableRow } from './tumaet-ui-chart-data-table.component';

/** Measures a chart only once its SVG exists, and owns the observer for the component's lifetime. */
export function observeChartSize(canvas: Signal<ElementRef<SVGSVGElement>>): Signal<{ width: number; height: number }> {
    const destroyRef = inject(DestroyRef);
    const size = signal({ width: 0, height: 0 });
    let observer: ResizeObserver | undefined;
    afterNextRender(() => {
        const element = canvas().nativeElement;
        const rect = element.getBoundingClientRect();
        size.set({ width: rect.width, height: rect.height });
        if (typeof ResizeObserver === 'undefined') {
            return;
        }
        observer = new ResizeObserver((entries) => {
            const box = entries[0]?.contentRect;
            if (box) {
                size.set({ width: box.width, height: box.height });
            }
        });
        observer.observe(element);
    });
    destroyRef.onDestroy(() => observer?.disconnect());
    return size.asReadonly();
}

/** Legend keys remain stable when entries are hidden, so colors and selection metadata retain their original index. */
export function createChartVisibility() {
    const hidden = signal<ReadonlySet<string>>(new Set());
    return {
        hidden: hidden.asReadonly(),
        toggle(key: string): void {
            hidden.update((current) => {
                const next = new Set(current);
                if (!next.delete(key)) {
                    next.add(key);
                }
                return next;
            });
        },
    };
}

export function visibleChartSeries(series: Signal<readonly TumAetUiChartSeries[]>, hidden: Signal<ReadonlySet<string>>) {
    return computed(() =>
        series()
            .map((entry, index) => ({ entry, index }))
            .filter(({ index }) => !hidden().has(`${index}`)),
    );
}

/** The accessible table reports all supplied values, including series hidden through the visual legend. */
export function chartDataRows(labels: readonly string[], series: readonly TumAetUiChartSeries[]): TumAetUiChartDataTableRow[] {
    return labels.map((label, index) => ({ label, values: series.map((entry) => ({ seriesLabel: entry.label, value: entry.data[index] })) }));
}
