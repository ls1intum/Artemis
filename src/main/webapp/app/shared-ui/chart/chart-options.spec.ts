import { describe, expect, it } from 'vitest';
import { Scale, TooltipItem } from 'chart.js';
import { barChartOptions, doughnutChartOptions, lineChartOptions, toChartSelectEvent } from 'app/shared-ui/chart/chart-options';
import { singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';

describe('chart-options', () => {
    describe('barChartOptions', () => {
        it('builds vertical bar options with axis labels and max', () => {
            const options = barChartOptions({
                xAxis: { label: 'Questions' },
                yAxis: { label: 'Students', max: 50, tickFormatter: (value) => `${value}!` },
                tooltip: false,
            });

            expect(options.indexAxis).toBe('x');
            expect(options.maintainAspectRatio).toBe(false);
            expect((options.scales!.x as any).title).toEqual({ display: true, text: 'Questions' });
            expect((options.scales!.y as any).max).toBe(50);
            expect((options.plugins!.tooltip as any).enabled).toBe(false);
            expect((options.plugins!.legend as any).display).toBe(false);
            expect((options.plugins as any).datalabels.display).toBe(false);
        });

        it('applies the tick formatter, resolving category labels through the scale', () => {
            const options = barChartOptions({ yAxis: { tickFormatter: (value) => `${value} %` } });
            const callback = (options.scales!.y as any).ticks.callback;

            // linear axis: the numeric tick value is passed through
            expect(callback.call({} as Scale, 42)).toBe('42 %');
        });

        it('resolves category ticks via getLabelForValue', () => {
            const options = barChartOptions({ xAxis: { tickFormatter: (value) => `${value}` } });
            const callback = (options.scales!.x as any).ticks.callback;
            const scale = { getLabelForValue: (index: number) => `Label ${index}` } as unknown as Scale;

            expect(callback.call(scale, 3)).toBe('Label 3');
        });

        it('configures horizontal stacked bars with percent scale', () => {
            const options = barChartOptions({ horizontal: true, stacked: true, percentScale: true });

            expect(options.indexAxis).toBe('y');
            expect((options.scales!.x as any).stacked).toBe(true);
            expect((options.scales!.y as any).stacked).toBe(true);
            expect((options.scales!.x as any).max).toBe(100);
            const callback = (options.scales!.x as any).ticks.callback;
            expect(callback.call({} as Scale, 50)).toBe('50%');
        });

        it('enables data labels with the given formatter', () => {
            const options = barChartOptions({ dataLabels: { formatter: (value) => `#${value}` } });
            const datalabels = (options.plugins as any).datalabels;

            expect(datalabels.display).toBe(true);
            expect(datalabels.anchor).toBe('end');
            expect(datalabels.clamp).toBe(true);
            expect(datalabels.formatter(7, {} as any)).toBe('#7');
        });

        it('shows the legend at the requested position', () => {
            const options = barChartOptions({ legend: { position: 'bottom' } });

            expect((options.plugins!.legend as any).display).toBe(true);
            expect((options.plugins!.legend as any).position).toBe('bottom');
        });
    });

    describe('lineChartOptions', () => {
        it('uses index interaction mode and wires tooltip callbacks', () => {
            const title = (items: TooltipItem<'line'>[]) => `Week ${items[0]?.label}`;
            const options = lineChartOptions({ tooltip: { title }, yAxis: { min: 0, max: 100 } });

            expect(options.interaction!.mode).toBe('index');
            expect(options.interaction!.intersect).toBe(false);
            expect((options.plugins!.tooltip as any).callbacks.title).toBe(title);
            expect((options.scales!.y as any).max).toBe(100);
        });

        it('filters reference line datasets out of the legend', () => {
            const options = lineChartOptions({ legend: true });
            const filter = (options.plugins!.legend as any).labels.filter;
            const data = { datasets: [{ label: 'Scores' }, { label: 'Average', referenceLine: true }] };

            expect(filter({ datasetIndex: 0 }, data)).toBe(true);
            expect(filter({ datasetIndex: 1 }, data)).toBe(false);
            // pie legend items have no datasetIndex and are always kept
            expect(filter({}, data)).toBe(true);
        });
    });

    describe('doughnutChartOptions', () => {
        it('maps arcWidth to the cutout percentage', () => {
            expect(doughnutChartOptions({}).cutout).toBe('75%');
            expect(doughnutChartOptions({ arcWidth: 0.4 }).cutout).toBe('60%');
            // arcWidth 1 yields a full pie
            expect(doughnutChartOptions({ arcWidth: 1 }).cutout).toBe('0%');
        });
    });

    describe('toChartSelectEvent', () => {
        const data = singleSeriesChartData(
            [
                { name: 'A', value: 10, id: 7 },
                { name: 'B', value: 20 },
            ],
            ['red'],
            'Scores',
        );

        it('maps the p-chart event payload to label, value, and meta', () => {
            const event = toChartSelectEvent({ element: { datasetIndex: 0, index: 0 } }, data);

            expect(event).toEqual({
                datasetIndex: 0,
                index: 0,
                label: 'A',
                datasetLabel: 'Scores',
                value: 10,
                meta: { name: 'A', value: 10, id: 7 },
            });
        });

        it('returns undefined when no element was hit', () => {
            expect(toChartSelectEvent({}, data)).toBeUndefined();
            expect(toChartSelectEvent({ element: undefined }, data)).toBeUndefined();
        });

        it('returns undefined for clicks on reference lines', () => {
            const withReference = { labels: ['A'], datasets: [{ data: [1], referenceLine: true }] };

            expect(toChartSelectEvent({ element: { datasetIndex: 0, index: 0 } }, withReference as any)).toBeUndefined();
        });
    });
});
