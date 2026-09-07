import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiLineChartComponent } from './tum-ui-line-chart.component';

const weeks = ['Week 1', 'Week 2', 'Week 3', 'Week 4', 'Week 5', 'Week 6'];

const meta = {
    title: 'Data/Line Chart',
    component: TumUiLineChartComponent,
    args: {
        ariaLabel: 'Active students',
        labels: weeks,
        series: [{ label: 'Active students', data: [42, 51, 47, 63, 58, 71], color: 'var(--tumaet-ui-primary-color)' }],
        config: { yAxis: { label: 'Students' }, monotone: true },
    },
    parameters: {
        layout: 'padded',
    },
    decorators: [
        (story) => ({
            ...story(),
            template: `<div style="height: 320px">${story().template ?? ''}</div>`,
        }),
    ],
} satisfies Meta<TumUiLineChartComponent>;

export default meta;

type Story = StoryObj<TumUiLineChartComponent>;

export const Default: Story = {};

export const MultipleSeriesWithReferenceLine: Story = {
    args: {
        ariaLabel: 'Exercise scores',
        series: [
            { label: 'Your score', data: [62, 71, 55, 88, 79, 92], color: 'var(--tumaet-ui-primary-color)' },
            { label: 'Average', data: [58, 64, 60, 72, 70, 75], color: 'hsl(45, 80%, 45%)' },
            { label: 'Best score', data: [90, 95, 88, 99, 96, 100], color: 'hsl(140, 55%, 40%)' },
            { label: 'Course average', data: [72, 72, 72, 72, 72, 72], color: 'var(--tumaet-ui-muted-color)', referenceLine: true },
        ],
        config: {
            legend: { position: 'right' },
            yAxis: { label: 'Score', min: 0, max: 100, tickFormatter: (value) => `${value}%` },
        },
    },
};

export const WithGaps: Story = {
    args: {
        ariaLabel: 'Submissions with missing weeks',
        series: [{ label: 'Submissions', data: [30, undefined, 55, 60, undefined, 72], color: 'var(--tumaet-ui-primary-color)' }],
        config: { yAxis: { label: 'Submissions' } },
    },
};

export const SpanningGaps: Story = {
    args: {
        ariaLabel: 'Submissions spanning missing weeks',
        series: [{ label: 'Submissions', data: [30, undefined, 55, 60, undefined, 72], color: 'var(--tumaet-ui-primary-color)' }],
        config: { yAxis: { label: 'Submissions' }, spanGaps: true, monotone: true },
    },
};
