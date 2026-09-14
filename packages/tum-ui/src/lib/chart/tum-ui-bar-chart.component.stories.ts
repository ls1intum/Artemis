import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiBarChartComponent } from './tum-ui-bar-chart.component';

const scoreBuckets = ['[0, 20)', '[20, 40)', '[40, 60)', '[60, 80)', '[80, 100]'];

const meta = {
    title: 'Data/Bar Chart',
    component: TumUiBarChartComponent,
    args: {
        ariaLabel: 'Score distribution',
        labels: scoreBuckets,
        series: [{ label: 'Students', data: [4, 9, 18, 27, 12], color: 'var(--tumaet-ui-primary-color)' }],
        config: {
            yAxis: { label: 'Students' },
            xAxis: { label: 'Score' },
            tooltip: { label: (item) => `${item.value} students` },
        },
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
} satisfies Meta<TumUiBarChartComponent>;

export default meta;

type Story = StoryObj<TumUiBarChartComponent>;

export const Default: Story = {};

export const WithDataLabels: Story = {
    args: {
        config: {
            percentScale: true,
            dataLabels: { formatter: (value) => `${value}%` },
            tooltip: { label: (item) => `${item.value}%` },
        },
        series: [{ label: 'Share', data: [8, 15, 34, 51, 22], color: 'var(--tumaet-ui-primary-color)' }],
    },
};

export const StackedHorizontal: Story = {
    args: {
        ariaLabel: 'Test case weights',
        labels: ['Weight', 'Weight and bonus'],
        series: [
            { label: 'testSortStable', data: [40, 30], color: 'hsl(0, 55%, 50%)' },
            { label: 'testMergeSort', data: [35, 45], color: 'hsl(120, 55%, 50%)' },
            { label: 'testEdgeCases', data: [25, 25], color: 'hsl(240, 55%, 50%)' },
        ],
        config: {
            horizontal: true,
            stacked: true,
            percentScale: true,
            legend: { position: 'bottom' },
            tooltip: { title: (items) => items[0].seriesLabel ?? '', label: (item) => `${item.value.toFixed(2)}%` },
        },
    },
};

export const GroupedSeries: Story = {
    args: {
        ariaLabel: 'Average score per exercise type',
        labels: ['Quiz', 'Modeling', 'Programming', 'Text'],
        series: [
            { label: 'Winter 25/26', data: [82, 64, 71, 77], color: 'var(--tumaet-ui-primary-color)' },
            { label: 'Summer 26', data: [88, 59, 76, 74], color: 'hsl(200, 55%, 45%)' },
        ],
        config: { legend: { position: 'right' }, yAxis: { max: 100 } },
    },
};

export const ManyCategories: Story = {
    args: {
        ariaLabel: 'Submissions per exercise',
        labels: ['Sorting Algorithms', 'Binary Trees', 'Graph Traversal', 'Dynamic Programming', 'Hash Tables', 'String Matching', 'Greedy Algorithms'],
        series: [{ label: 'Submissions', data: [120, 98, 87, 64, 110, 73, 91], color: 'var(--tumaet-ui-primary-color)' }],
        config: { yAxis: { label: 'Submissions' } },
    },
};
