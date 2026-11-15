import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';
import { TumAetUiDoughnutChartComponent } from './tumaet-ui-doughnut-chart.component';
import { TumAetUiChartSelectEvent, TumAetUiChartSeries, TumAetUiDoughnutChartConfig } from './tumaet-ui-chart.types';

describe('TumAetUiDoughnutChartComponent', () => {
    let fixture: ComponentFixture<TumAetUiDoughnutChartComponent>;

    const labels = ['Assessed', 'Open', 'Locked'];

    async function render(series: TumAetUiChartSeries[], config: TumAetUiDoughnutChartConfig = {}): Promise<void> {
        fixture.componentRef.setInput('labels', labels);
        fixture.componentRef.setInput('series', series);
        fixture.componentRef.setInput('config', config);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    }

    function slices(): SVGPathElement[] {
        return fixture.debugElement.queryAll(By.css('path.tumaet-ui-doughnut-chart-slice')).map((element) => element.nativeElement);
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumAetUiDoughnutChartComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumAetUiDoughnutChartComponent);
        vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
            width: 300,
            height: 300,
            top: 0,
            left: 0,
            right: 300,
            bottom: 300,
            x: 0,
            y: 0,
            toJSON: () => ({}),
        } as DOMRect);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render one slice per category', async () => {
        await render([{ data: [30, 50, 20], colors: ['var(--graph-green)', 'var(--graph-yellow)', 'var(--graph-red)'] }]);
        expect(slices()).toHaveLength(3);
        expect(slices()[0].getAttribute('fill')).toBe('var(--graph-green)');
    });

    it('should draw a ring by default and a filled pie at an arc width of 1', async () => {
        await render([{ data: [30, 50, 20] }]);
        // A ring is closed with a second, inner arc.
        expect(slices()[0].getAttribute('d')!.match(/A/g)).toHaveLength(2);

        await render([{ data: [30, 50, 20] }], { arcWidth: 1 });
        const pie = slices()[0].getAttribute('d')!;
        expect(pie.match(/A/g)).toHaveLength(1);
        expect(pie.startsWith('M150,150')).toBe(true);
    });

    it('should draw a single full-circle value as two arcs so it is visible', async () => {
        await render([{ data: [100] }]);
        expect(slices()[0].getAttribute('d')!.match(/A/g)).toHaveLength(4);
    });

    it('should render nothing but empty slices when every value is zero', async () => {
        await render([{ data: [0, 0, 0] }]);
        expect(slices().every((slice) => slice.getAttribute('d') === '')).toBe(true);
    });

    it('should shrink the ring by the configured padding', async () => {
        await render([{ data: [30, 50, 20] }], { padding: 0 });
        const withoutPadding = slices()[0].getAttribute('d')!;
        await render([{ data: [30, 50, 20] }], { padding: 40 });
        const withPadding = slices()[0].getAttribute('d')!;
        expect(withoutPadding).not.toBe(withPadding);
    });

    it('should name the slices in the legend', async () => {
        await render([{ data: [30, 50, 20] }], { legend: { position: 'bottom' } });
        const legend = fixture.debugElement.queryAll(By.css('.tumaet-ui-chart-legend-item')).map((element) => (element.nativeElement as HTMLElement).textContent!.trim());
        expect(legend).toEqual(labels);
    });

    it('should show a tooltip built from the configured callback', async () => {
        await render([{ data: [30, 50, 20] }], { tooltip: { label: (item) => `${item.value} submissions` } });
        slices()[1].dispatchEvent(new MouseEvent('mouseenter', { clientX: 40, clientY: 40 }));
        fixture.detectChanges();
        const tooltip = fixture.debugElement.query(By.css('tumaet-ui-chart-tooltip')).nativeElement as HTMLElement;
        expect(tooltip.textContent).toContain('Open');
        expect(tooltip.textContent).toContain('50 submissions');
    });

    it('should emit the clicked slice with its metadata', async () => {
        await render([{ data: [30, 50, 20], meta: [{ id: 'a' }, { id: 'b' }, { id: 'c' }] }]);
        let emitted: TumAetUiChartSelectEvent | undefined;
        fixture.componentInstance.dataSelect.subscribe((event) => (emitted = event));
        slices()[2].dispatchEvent(new MouseEvent('click'));
        expect(emitted).toEqual({ seriesIndex: 0, index: 2, label: 'Locked', seriesLabel: undefined, value: 20, meta: { id: 'c' } });
    });

    it('should expose the values in a table for assistive technology', async () => {
        await render([{ label: 'Submissions', data: [30, 50, 20] }]);
        const rows = fixture.debugElement.queryAll(By.css('tumaet-ui-chart-data-table tbody tr'));
        expect(rows).toHaveLength(3);
        expect((rows[1].nativeElement as HTMLElement).textContent).toContain('50');
    });
});
