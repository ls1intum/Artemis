import { Component, ElementRef, signal, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { chartDataRows, createChartVisibility, observeChartSize, visibleChartSeries } from './tumaet-ui-chart-state';
import { TumAetUiChartSeries } from './tumaet-ui-chart.types';

// The SVG template supplies the view query that the resize lifecycle observes after rendering.
@Component({ template: '<svg #canvas />' })
class ChartSizeProbe {
    private readonly canvas = viewChild.required<ElementRef<SVGSVGElement>>('canvas');
    readonly size = observeChartSize(this.canvas);
}

describe('chart state', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('keeps original series indices and identities across legend toggles and data updates', () => {
        const first = { label: 'First', data: [1, 2] };
        const second = { label: 'Second', data: [3] };
        const series = signal<readonly TumAetUiChartSeries[]>([first, second]);
        const visibility = createChartVisibility();
        const visible = visibleChartSeries(series, visibility.hidden);
        const initialHidden = visibility.hidden();
        visibility.toggle('0');
        expect(initialHidden.size).toBe(0);
        expect(visible()).toEqual([{ entry: second, index: 1 }]);
        expect(visible()[0].entry).toBe(second);
        series.set([second, first]);
        expect(visible()).toEqual([{ entry: first, index: 1 }]);
        visibility.toggle('0');
        expect(visible().map(({ index }) => index)).toEqual([0, 1]);
        expect(createChartVisibility().hidden().size).toBe(0);
    });

    it('preserves missing values and every series in the accessible table', () => {
        expect(chartDataRows(['A', 'B'], [{ label: 'First', data: [0, -2] }, { data: [4] }])).toEqual([
            {
                label: 'A',
                values: [
                    { seriesLabel: 'First', value: 0 },
                    { seriesLabel: undefined, value: 4 },
                ],
            },
            {
                label: 'B',
                values: [
                    { seriesLabel: 'First', value: -2 },
                    { seriesLabel: undefined, value: undefined },
                ],
            },
        ]);
        expect(chartDataRows([], [])).toEqual([]);
    });

    it('measures after rendering, follows resize notifications and disconnects when destroyed', async () => {
        let callback: ResizeObserverCallback | undefined;
        const observe = vi.fn();
        const disconnect = vi.fn();
        const observer: ResizeObserver = { observe, disconnect, unobserve: vi.fn() };
        vi.stubGlobal(
            'ResizeObserver',
            class {
                constructor(next: ResizeObserverCallback) {
                    callback = next;
                    return observer;
                }
            },
        );
        vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 600, 300));
        const fixture = TestBed.createComponent(ChartSizeProbe);
        expect(fixture.componentInstance.size()).toEqual({ width: 0, height: 0 });
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.componentInstance.size()).toEqual({ width: 600, height: 300 });
        expect(observe).toHaveBeenCalledWith(fixture.nativeElement.querySelector('svg'));
        callback!([{ contentRect: new DOMRect(0, 0, 400, 200) } as ResizeObserverEntry], observer);
        expect(fixture.componentInstance.size()).toEqual({ width: 400, height: 200 });
        callback!([], observer);
        expect(fixture.componentInstance.size()).toEqual({ width: 400, height: 200 });
        fixture.destroy();
        expect(disconnect).toHaveBeenCalledOnce();
    });

    it('retains the initial measurement when ResizeObserver is unavailable', async () => {
        vi.stubGlobal('ResizeObserver', undefined);
        vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue(new DOMRect(0, 0, 80, 40));
        const fixture = TestBed.createComponent(ChartSizeProbe);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(fixture.componentInstance.size()).toEqual({ width: 80, height: 40 });
        expect(() => fixture.destroy()).not.toThrow();
    });
});
