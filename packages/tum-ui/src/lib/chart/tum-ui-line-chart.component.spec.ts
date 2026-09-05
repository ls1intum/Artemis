import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { TumUiLineChartComponent } from './tum-ui-line-chart.component';
import { TumUiChartSelectEvent, TumUiChartSeries, TumUiLineChartConfig } from './tum-ui-chart.types';

describe('TumUiLineChartComponent', () => {
    let fixture: ComponentFixture<TumUiLineChartComponent>;

    const labels = ['Week 1', 'Week 2', 'Week 3', 'Week 4'];

    async function render(series: TumUiChartSeries[], config: TumUiLineChartConfig = {}): Promise<void> {
        fixture.componentRef.setInput('labels', labels);
        fixture.componentRef.setInput('series', series);
        fixture.componentRef.setInput('config', config);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    function paths(): SVGPathElement[] {
        return fixture.debugElement.queryAll(By.css('path.tum-ui-line-chart-line')).map((element) => element.nativeElement);
    }

    function texts(selector: string): string[] {
        return fixture.debugElement.queryAll(By.css(selector)).map((element) => (element.nativeElement as SVGTextElement).textContent!.trim());
    }

    function hitArea(): SVGRectElement {
        return fixture.debugElement.query(By.css('rect.tum-ui-line-chart-hit-area')).nativeElement;
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiLineChartComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumUiLineChartComponent);
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

    it('should lead an interactive point with its series so that two series stay distinguishable', async () => {
        fixture.componentRef.setInput('interactive', true);
        await render([
            { label: 'Your score', data: [40, 55, 60, 72] },
            { label: 'Average', data: [40, 52, 58, 61] },
        ]);
        const names = fixture.debugElement
            .queryAll(By.css('circle.tum-ui-line-chart-point'))
            .map((element) => (element.nativeElement as SVGCircleElement).getAttribute('aria-label'));
        expect(names).toContain('Your score, Week 1: 40');
        expect(names).toContain('Average, Week 1: 40');
    });

    it('should name an interactive point by category alone when there is only one series', async () => {
        fixture.componentRef.setInput('interactive', true);
        await render([{ label: 'Your score', data: [40, 55, 60, 72] }]);
        const names = fixture.debugElement
            .queryAll(By.css('circle.tum-ui-line-chart-point'))
            .map((element) => (element.nativeElement as SVGCircleElement).getAttribute('aria-label'));
        expect(names).toContain('Week 1: 40');
    });

    it('should render one path per series', async () => {
        await render([
            { label: 'Your score', data: [40, 55, 60, 72], color: 'var(--graph-blue)' },
            { label: 'Average', data: [50, 52, 58, 61], color: 'var(--graph-yellow)' },
        ]);
        expect(paths()).toHaveLength(2);
        expect(paths()[0].getAttribute('stroke')).toBe('var(--graph-blue)');
    });

    it('should render selectable axis labels as real text nodes', async () => {
        await render([{ data: [40, 55, 60, 72] }]);
        const rendered = texts('text.tum-ui-chart-tick');
        for (const label of labels) {
            expect(rendered).toContain(label);
        }
    });

    it('should draw straight segments by default and curves when monotone', async () => {
        await render([{ data: [40, 55, 60, 72] }]);
        expect(paths()[0].getAttribute('d')).toContain('L');
        expect(paths()[0].getAttribute('d')).not.toContain('C');

        await render([{ data: [40, 55, 60, 72] }], { monotone: true });
        expect(paths()[0].getAttribute('d')).toContain('C');
    });

    it('should break the line into separate paths at a gap', async () => {
        await render([{ data: [40, undefined, 60, 72] }]);
        expect(paths()).toHaveLength(2);
    });

    it('should draw across a gap when spanGaps is set', async () => {
        await render([{ data: [40, undefined, 60, 72] }], { spanGaps: true });
        expect(paths()).toHaveLength(1);
    });

    it('should render a point per defined value', async () => {
        await render([{ data: [40, undefined, 60, 72] }]);
        expect(fixture.debugElement.queryAll(By.css('circle.tum-ui-line-chart-point'))).toHaveLength(3);
    });

    it('should draw a reference line dashed and keep it out of the legend', async () => {
        await render(
            [
                { label: 'Your score', data: [40, 55, 60, 72], color: 'var(--graph-blue)' },
                { label: 'Average', data: [55, 55, 55, 55], color: 'var(--graph-grey)', referenceLine: true },
            ],
            { legend: { position: 'right' } },
        );
        const dashed = fixture.debugElement.queryAll(By.css('path.tum-ui-line-chart-line-dashed'));
        expect(dashed).toHaveLength(1);
        const legend = fixture.debugElement.queryAll(By.css('.tum-ui-chart-legend-item')).map((element) => (element.nativeElement as HTMLElement).textContent!.trim());
        expect(legend).toEqual(['Your score']);
    });

    it('should report every series at the hovered category', async () => {
        await render([
            { label: 'Your score', data: [40, 55, 60, 72] },
            { label: 'Average', data: [50, 52, 58, 61] },
        ]);
        // The third of four categories sits around 62% across the plot.
        hitArea().dispatchEvent(new MouseEvent('mousemove', { clientX: 380, clientY: 100 }));
        fixture.detectChanges();
        const tooltip = fixture.debugElement.query(By.css('tum-ui-chart-tooltip')).nativeElement as HTMLElement;
        expect(tooltip.textContent).toContain('Week 3');
        expect(tooltip.textContent).toContain('Your score: 60');
        expect(tooltip.textContent).toContain('Average: 58');
    });

    it('should leave a reference line out of the tooltip', async () => {
        await render([
            { label: 'Your score', data: [40, 55, 60, 72] },
            { label: 'Average', data: [55, 55, 55, 55], referenceLine: true },
        ]);
        hitArea().dispatchEvent(new MouseEvent('mousemove', { clientX: 380, clientY: 100 }));
        fixture.detectChanges();
        const tooltip = fixture.debugElement.query(By.css('tum-ui-chart-tooltip')).nativeElement as HTMLElement;
        expect(tooltip.textContent).not.toContain('Average');
    });

    it('should append afterBody lines to the tooltip', async () => {
        await render([{ label: 'Your score', data: [40, 55, 60, 72], meta: [{ type: 'quiz' }, {}, {}, {}] }], {
            tooltip: { afterBody: (items) => `Type: ${(items[0].meta as { type?: string }).type ?? '-'}` },
        });
        hitArea().dispatchEvent(new MouseEvent('mousemove', { clientX: 10, clientY: 100 }));
        fixture.detectChanges();
        const tooltip = fixture.debugElement.query(By.css('tum-ui-chart-tooltip')).nativeElement as HTMLElement;
        expect(tooltip.textContent).toContain('Type: quiz');
    });

    /** Translates a rendered point back into the client coordinates a real click would carry. */
    function clientPositionOf(point: SVGCircleElement): { clientX: number; clientY: number } {
        const group = fixture.debugElement.query(By.css('svg > g')).nativeElement as SVGGElement;
        const [left, top] = (group.getAttribute('transform') ?? '').match(/-?\d+(\.\d+)?/g)!.map(Number);
        return { clientX: left + Number(point.getAttribute('cx')), clientY: top + Number(point.getAttribute('cy')) };
    }

    it('should emit the point that was clicked', async () => {
        await render([
            { label: 'Your score', data: [40, 55, 60, 72], meta: [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }] },
            { label: 'Average', data: [50, 52, 58, 61] },
        ]);
        let emitted: TumUiChartSelectEvent | undefined;
        fixture.componentInstance.dataSelect.subscribe((event) => (emitted = event));
        const first = fixture.debugElement.query(By.css('circle.tum-ui-line-chart-point')).nativeElement as SVGCircleElement;
        const at = clientPositionOf(first);
        hitArea().dispatchEvent(new MouseEvent('mousemove', at));
        fixture.detectChanges();
        hitArea().dispatchEvent(new MouseEvent('click', at));
        expect(emitted?.index).toBe(0);
        expect(emitted?.label).toBe('Week 1');
        expect(emitted?.value).toBe(40);
        expect(emitted?.meta).toEqual({ id: 1 });
    });

    /**
     * The hit area spans the plot so hovering anywhere reports a category, but a click often
     * navigates, so an empty patch of chart must not select the nearest point from far away.
     */
    it('should not emit for a click far away from any point', async () => {
        await render([{ label: 'Your score', data: [40, 55, 60, 72] }]);
        let emitted: TumUiChartSelectEvent | undefined;
        fixture.componentInstance.dataSelect.subscribe((event) => (emitted = event));
        const first = fixture.debugElement.query(By.css('circle.tum-ui-line-chart-point')).nativeElement as SVGCircleElement;
        const at = clientPositionOf(first);
        const far = { clientX: at.clientX, clientY: at.clientY + 150 };
        hitArea().dispatchEvent(new MouseEvent('mousemove', far));
        fixture.detectChanges();
        hitArea().dispatchEvent(new MouseEvent('click', far));
        expect(emitted).toBeUndefined();
    });

    it('should honour an explicit value axis range', async () => {
        await render([{ data: [40, 55, 60, 72] }], { yAxis: { min: 0, max: 100, tickFormatter: (value) => `${value}%` } });
        const ticks = texts('text.tum-ui-chart-tick');
        expect(ticks).toContain('0%');
        expect(ticks).toContain('100%');
    });

    it('should truncate category labels with the axis formatter', async () => {
        await render([{ data: [40, 55, 60, 72] }], { xAxis: { tickFormatter: (value) => `${value}`.slice(0, 4) } });
        expect(texts('text.tum-ui-chart-tick')).toContain('Week');
    });

    it('should expose the values in a table for assistive technology', async () => {
        await render([{ label: 'Your score', data: [40, 55, 60, 72] }]);
        const rows = fixture.debugElement.queryAll(By.css('tum-ui-chart-data-table tbody tr'));
        expect(rows).toHaveLength(4);
        expect((rows[3].nativeElement as HTMLElement).textContent).toContain('72');
    });
});
