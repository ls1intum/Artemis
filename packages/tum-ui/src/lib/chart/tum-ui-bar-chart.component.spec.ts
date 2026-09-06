import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { TumUiBarChartComponent } from './tum-ui-bar-chart.component';
import { TumUiBarChartConfig, TumUiChartSelectEvent, TumUiChartSeries } from './tum-ui-chart.types';

describe('TumUiBarChartComponent', () => {
    let fixture: ComponentFixture<TumUiBarChartComponent>;

    const labels = ['[0, 10)', '[10, 20)', '[20, 30)'];

    async function render(series: TumUiChartSeries[], config: TumUiBarChartConfig = {}): Promise<void> {
        fixture.componentRef.setInput('labels', labels);
        fixture.componentRef.setInput('series', series);
        fixture.componentRef.setInput('config', config);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    function rects(): SVGRectElement[] {
        return fixture.debugElement.queryAll(By.css('rect.tum-ui-bar-chart-bar')).map((element) => element.nativeElement);
    }

    function texts(selector: string): string[] {
        return fixture.debugElement.queryAll(By.css(selector)).map((element) => (element.nativeElement as SVGTextElement).textContent!.trim());
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiBarChartComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumUiBarChartComponent);
        // jsdom reports zero-sized elements, which would collapse the plot area to nothing.
        vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
            width: 600,
            height: 300,
            top: 0,
            left: 0,
            right: 600,
            bottom: 300,
            x: 0,
            y: 0,
            toJSON: () => ({}),
        } as DOMRect);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render one bar per data point', async () => {
        await render([{ data: [10, 20, 30], color: 'var(--graph-blue)' }]);
        expect(rects()).toHaveLength(3);
    });

    it('should render category and value labels as real, selectable text nodes', async () => {
        await render([{ data: [10, 20, 30] }]);
        const categories = texts('text.tum-ui-chart-tick');
        for (const label of labels) {
            expect(categories).toContain(label);
        }
        // Value ticks are present alongside the category labels.
        expect(categories.length).toBeGreaterThan(labels.length);
    });

    it('should pass CSS custom property colors straight through to the SVG fill', async () => {
        await render([{ data: [1, 2, 3], color: 'var(--graph-dark-blue)' }]);
        expect(rects()[0].getAttribute('fill')).toBe('var(--graph-dark-blue)');
    });

    it('should apply per-category colors when provided', async () => {
        await render([{ data: [1, 2, 3], colors: ['var(--graph-red)', 'var(--graph-green)', 'var(--graph-blue)'] }]);
        expect(rects().map((rect) => rect.getAttribute('fill'))).toEqual(['var(--graph-red)', 'var(--graph-green)', 'var(--graph-blue)']);
    });

    it('should grow bars downwards from the value axis for taller values', async () => {
        await render([{ data: [10, 50, 100] }]);
        const heights = rects().map((rect) => Number(rect.getAttribute('height')));
        expect(heights[0]).toBeLessThan(heights[1]);
        expect(heights[1]).toBeLessThan(heights[2]);
    });

    it('should lead an interactive bar with its series so that two stacked segments stay distinguishable', async () => {
        fixture.componentRef.setInput('interactive', true);
        await render(
            [
                { label: 'passed', data: [10, 10, 10] },
                { label: 'failed', data: [10, 5, 5] },
            ],
            { stacked: true },
        );
        const names = rects().map((rect) => rect.getAttribute('aria-label'));
        expect(names).toContain('passed, [0, 10): 10');
        expect(names).toContain('failed, [0, 10): 10');
    });

    it('should name an interactive bar by category alone when there is only one series', async () => {
        fixture.componentRef.setInput('interactive', true);
        await render([{ label: 'Number of students', data: [10, 50, 100] }]);
        expect(rects().map((rect) => rect.getAttribute('aria-label'))).toContain('[0, 10): 10');
    });

    it('should stack series on top of each other when stacked', async () => {
        await render(
            [
                { label: 'passed', data: [10, 10, 10], color: 'var(--graph-green)' },
                { label: 'failed', data: [5, 5, 5], color: 'var(--graph-red)' },
            ],
            { stacked: true },
        );
        const all = rects();
        expect(all).toHaveLength(6);
        const firstCategoryBars = [all[0], all[3]];
        // The second series sits directly on top of the first, so its bar ends where the first begins.
        const lower = Number(firstCategoryBars[0].getAttribute('y'));
        const upper = Number(firstCategoryBars[1].getAttribute('y'));
        expect(upper).toBeLessThan(lower);
        expect(upper + Number(firstCategoryBars[1].getAttribute('height'))).toBeCloseTo(lower, 5);
    });

    it('should place bars side by side when several series are not stacked', async () => {
        await render([
            { label: 'a', data: [10, 10, 10] },
            { label: 'b', data: [5, 5, 5] },
        ]);
        const all = rects();
        expect(Number(all[0].getAttribute('x'))).toBeLessThan(Number(all[3].getAttribute('x')));
    });

    it('should swap the axes for horizontal bars', async () => {
        await render([{ data: [10, 20, 30] }], { horizontal: true });
        const widths = rects().map((rect) => Number(rect.getAttribute('width')));
        const heights = rects().map((rect) => Number(rect.getAttribute('height')));
        expect(widths[0]).toBeLessThan(widths[2]);
        expect(new Set(heights).size).toBe(1);
    });

    it('should cap bar thickness at maxBarThickness', async () => {
        await render([{ data: [10, 20, 30] }], { horizontal: true, maxBarThickness: 12 });
        expect(Number(rects()[0].getAttribute('height'))).toBeLessThanOrEqual(12);
    });

    it('should render data labels using the configured formatter', async () => {
        await render([{ data: [10, 20, 30] }], { dataLabels: { formatter: (value) => `${value}%` } });
        expect(texts('text.tum-ui-bar-chart-data-label')).toEqual(['10%', '20%', '30%']);
    });

    it('should format value ticks with the axis formatter', async () => {
        await render([{ data: [10, 20, 30] }], { yAxis: { max: 100, tickFormatter: (value) => `${value} pts` } });
        expect(texts('text.tum-ui-chart-tick')).toContain('100 pts');
    });

    it('should append a percent sign to value ticks on a percent scale', async () => {
        await render([{ data: [10, 20, 30] }], { percentScale: true });
        expect(texts('text.tum-ui-chart-tick')).toContain('100%');
    });

    it('should hide an axis that is configured as not displayed', async () => {
        await render([{ data: [10, 20, 30] }], { horizontal: true, yAxis: { display: false } });
        expect(texts('text.tum-ui-chart-tick')).not.toContain(labels[0]);
    });

    it('should render the axis titles', async () => {
        await render([{ data: [1, 2, 3] }], { xAxis: { label: 'Score' }, yAxis: { label: 'Students' } });
        expect(texts('text.tum-ui-chart-axis-title')).toEqual(expect.arrayContaining(['Score', 'Students']));
    });

    it('should emit the clicked datum together with its metadata', async () => {
        const meta = [{ id: 1 }, { id: 2 }, { id: 3 }];
        await render([{ label: 'scores', data: [10, 20, 30], meta }]);
        let emitted: TumUiChartSelectEvent | undefined;
        fixture.componentInstance.dataSelect.subscribe((event) => (emitted = event));
        rects()[1].dispatchEvent(new MouseEvent('click'));
        expect(emitted).toEqual({ seriesIndex: 0, index: 1, label: '[10, 20)', seriesLabel: 'scores', value: 20, meta: { id: 2 } });
    });

    it('should show a tooltip built from the configured callbacks on hover', async () => {
        await render([{ data: [10, 20, 30] }], {
            tooltip: { title: (items) => `Bucket ${items[0].label}`, label: (item) => `${item.value} students` },
        });
        rects()[0].dispatchEvent(new MouseEvent('mouseenter', { clientX: 40, clientY: 40 }));
        fixture.detectChanges();
        const tooltip = fixture.debugElement.query(By.css('tum-ui-chart-tooltip')).nativeElement as HTMLElement;
        expect(tooltip.textContent).toContain('Bucket [0, 10)');
        expect(tooltip.textContent).toContain('10 students');
    });

    it('should not render a tooltip when tooltips are disabled', async () => {
        await render([{ data: [10, 20, 30] }], { tooltip: false });
        rects()[0].dispatchEvent(new MouseEvent('mouseenter', { clientX: 40, clientY: 40 }));
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('tum-ui-chart-tooltip'))).toBeNull();
    });

    it('should render a legend for labelled series when enabled', async () => {
        await render(
            [
                { label: 'passed', data: [1, 2, 3], color: 'var(--graph-green)' },
                { label: 'failed', data: [3, 2, 1], color: 'var(--graph-red)' },
            ],
            { legend: { position: 'bottom' } },
        );
        const items = fixture.debugElement.queryAll(By.css('.tum-ui-chart-legend-item')).map((element) => (element.nativeElement as HTMLElement).textContent!.trim());
        expect(items).toEqual(['passed', 'failed']);
    });

    it('should expose the values in a table for assistive technology', async () => {
        await render([{ label: 'students', data: [10, 20, 30] }]);
        const rows = fixture.debugElement.queryAll(By.css('tum-ui-chart-data-table tbody tr'));
        expect(rows).toHaveLength(3);
        expect((rows[2].nativeElement as HTMLElement).textContent).toContain('30');
    });

    /**
     * Two exercises may share a title, and an untitled one contributes an empty label. Addressing
     * bands by label put them on the same band, so one bar was drawn invisibly behind another.
     */
    it('should give repeated labels their own bar', async () => {
        fixture.componentRef.setInput('labels', ['Exercise', 'Exercise', 'Other']);
        fixture.componentRef.setInput('series', [{ data: [10, 20, 30] }]);
        fixture.componentRef.setInput('config', {});
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const xs = rects().map((rect) => rect.getAttribute('x'));
        expect(new Set(xs).size).toBe(3);
    });

    /** One missing figure in a server response used to blank the whole chart instead of one bar. */
    it('should drop a value it cannot place instead of blanking the chart', async () => {
        await render([{ data: [10, Number.NaN, 30] }]);

        const drawn = rects();
        expect(drawn).toHaveLength(2);
        expect(drawn.every((rect) => Number.isFinite(Number(rect.getAttribute('height'))))).toBe(true);
        expect(texts('text.tum-ui-chart-tick').length).toBeGreaterThan(0);
    });

    it('should hide a series when its legend entry is switched off', async () => {
        await render(
            [
                { label: 'passed', data: [10, 10, 10], color: 'var(--graph-green)' },
                { label: 'failed', data: [5, 5, 5], color: 'var(--graph-red)' },
            ],
            { legend: { position: 'bottom' } },
        );
        expect(rects()).toHaveLength(6);

        const entries = fixture.debugElement.queryAll(By.css('.tum-ui-chart-legend-item'));
        entries[1].nativeElement.click();
        fixture.detectChanges();

        expect(rects()).toHaveLength(3);
        expect(entries[1].nativeElement.getAttribute('aria-pressed')).toBe('false');
    });

    it('should skip data points without a value', async () => {
        await render([{ data: [10, undefined, 30] }]);
        expect(rects()).toHaveLength(2);
    });
});
