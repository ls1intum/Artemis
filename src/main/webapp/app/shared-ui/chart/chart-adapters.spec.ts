import { describe, expect, it } from 'vitest';
import {
    multiSeriesToLineData,
    multiSeriesToNormalizedStackedBarData,
    multiSeriesToStackedBarData,
    referenceLineDataset,
    singleSeriesChartData,
} from 'app/shared-ui/chart/chart-adapters';
import { ChartMultiSeriesEntry } from 'app/shared-ui/chart/chart-data.model';

describe('chart-adapters', () => {
    describe('singleSeriesChartData', () => {
        it('maps entries to labels, values, and per-entry colors', () => {
            const entries = [
                { name: 'A', value: 1, extra: 'x' },
                { name: 'B', value: 2 },
                { name: 'C', value: 3 },
            ];
            const data = singleSeriesChartData(entries, ['red', 'green'], 'My dataset');

            expect(data.labels).toEqual(['A', 'B', 'C']);
            expect(data.datasets).toHaveLength(1);
            expect(data.datasets[0].label).toBe('My dataset');
            expect(data.datasets[0].data).toEqual([1, 2, 3]);
            // colors cycle when fewer colors than entries are provided
            expect(data.datasets[0].backgroundColor).toEqual(['red', 'green', 'red']);
            expect(data.datasets[0].meta).toBe(entries);
        });
    });

    describe('multiSeriesToStackedBarData', () => {
        const entries: ChartMultiSeriesEntry[] = [
            {
                name: 'Bar 1',
                series: [
                    { name: 'Segment A', value: 10, id: 1 },
                    { name: 'Segment B', value: 30 },
                ],
            },
            {
                name: 'Bar 2',
                series: [{ name: 'Segment B', value: 5 }],
            },
        ];

        it('creates one dataset per distinct segment with zeros for missing categories', () => {
            const data = multiSeriesToStackedBarData(entries, ['red', 'green']);

            expect(data.labels).toEqual(['Bar 1', 'Bar 2']);
            expect(data.datasets).toHaveLength(2);
            expect(data.datasets[0].label).toBe('Segment A');
            expect(data.datasets[0].data).toEqual([10, 0]);
            expect(data.datasets[0].backgroundColor).toBe('red');
            expect(data.datasets[1].label).toBe('Segment B');
            expect(data.datasets[1].data).toEqual([30, 5]);
            expect(data.datasets[1].backgroundColor).toBe('green');
        });

        it('keeps original series entries as meta, with undefined for missing segments', () => {
            const data = multiSeriesToStackedBarData(entries, ['red', 'green']);

            expect(data.datasets[0].meta?.[0]).toEqual({ name: 'Segment A', value: 10, id: 1 });
            expect(data.datasets[0].meta?.[1]).toBeUndefined();
        });
    });

    describe('multiSeriesToNormalizedStackedBarData', () => {
        it('converts segment values to percentages of each bar total', () => {
            const entries: ChartMultiSeriesEntry[] = [
                {
                    name: 'Bar 1',
                    series: [
                        { name: 'A', value: 25 },
                        { name: 'B', value: 75 },
                    ],
                },
                {
                    name: 'Bar 2',
                    series: [
                        { name: 'A', value: 2 },
                        { name: 'B', value: 6 },
                    ],
                },
            ];
            const data = multiSeriesToNormalizedStackedBarData(entries, ['red', 'green']);

            expect(data.datasets[0].data).toEqual([25, 25]);
            expect(data.datasets[1].data).toEqual([75, 75]);
            // raw values stay available for tooltips
            expect(data.datasets[1].meta?.[1]).toEqual({ name: 'B', value: 6 });
        });

        it('keeps all segments at 0 for bars with a total of 0', () => {
            const entries: ChartMultiSeriesEntry[] = [
                {
                    name: 'Empty bar',
                    series: [
                        { name: 'A', value: 0 },
                        { name: 'B', value: 0 },
                    ],
                },
            ];
            const data = multiSeriesToNormalizedStackedBarData(entries, ['red']);

            expect(data.datasets[0].data).toEqual([0]);
            expect(data.datasets[1].data).toEqual([0]);
        });
    });

    describe('multiSeriesToLineData', () => {
        it('creates one dataset per entry over the union of all point names', () => {
            const entries: ChartMultiSeriesEntry[] = [
                {
                    name: 'Line 1',
                    series: [
                        { name: 'W1', value: 10 },
                        { name: 'W2', value: 20 },
                    ],
                },
                {
                    name: 'Line 2',
                    series: [
                        { name: 'W2', value: 5 },
                        { name: 'W3', value: 15 },
                    ],
                },
            ];
            const data = multiSeriesToLineData(entries, ['red', 'green'], { monotone: true });

            expect(data.labels).toEqual(['W1', 'W2', 'W3']);
            expect(data.datasets[0].data).toEqual([10, 20, null]);
            expect(data.datasets[1].data).toEqual([null, 5, 15]);
            expect(data.datasets[0].borderColor).toBe('red');
            expect(data.datasets[0].cubicInterpolationMode).toBe('monotone');
            expect(data.datasets[1].meta?.[0]).toBeUndefined();
            expect(data.datasets[1].meta?.[1]).toEqual({ name: 'W2', value: 5 });
        });

        it('preserves duplicate labels when all entries share the same series structure', () => {
            const entries: ChartMultiSeriesEntry[] = [
                {
                    name: 'Your Score',
                    series: [
                        { name: 'Homework 1', value: 80, exerciseId: 1 },
                        { name: 'Homework 1', value: 60, exerciseId: 2 },
                        { name: 'Quiz 1', value: 90, exerciseId: 3 },
                    ],
                },
                {
                    name: 'Average',
                    series: [
                        { name: 'Homework 1', value: 70, exerciseId: 1 },
                        { name: 'Homework 1', value: 50, exerciseId: 2 },
                        { name: 'Quiz 1', value: 85, exerciseId: 3 },
                    ],
                },
            ];
            const data = multiSeriesToLineData(entries, ['blue', 'yellow']);

            expect(data.labels).toEqual(['Homework 1', 'Homework 1', 'Quiz 1']);
            expect(data.datasets[0].data).toEqual([80, 60, 90]);
            expect(data.datasets[1].data).toEqual([70, 50, 85]);
            expect(data.datasets[0].meta?.[0]).toEqual({ name: 'Homework 1', value: 80, exerciseId: 1 });
            expect(data.datasets[0].meta?.[1]).toEqual({ name: 'Homework 1', value: 60, exerciseId: 2 });
            expect(data.datasets[1].meta?.[1]).toEqual({ name: 'Homework 1', value: 50, exerciseId: 2 });
        });
    });

    describe('referenceLineDataset', () => {
        it('creates a flat dashed dataset marked as reference line', () => {
            const dataset = referenceLineDataset('Average', 42, 3, 'grey');

            expect(dataset.data).toEqual([42, 42, 42]);
            expect(dataset.referenceLine).toBe(true);
            expect(dataset.borderDash).toEqual([5, 5]);
            expect(dataset.pointRadius).toBe(0);
        });
    });
});
