import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiDoughnutChartComponent } from './tum-ui-doughnut-chart.component';

const meta = {
    title: 'Data/Doughnut Chart',
    component: TumUiDoughnutChartComponent,
    args: {
        ariaLabel: 'Assessment progress',
        labels: ['Assessed', 'Open', 'Locked'],
        series: [{ label: 'Submissions', data: [64, 28, 8], colors: ['hsl(140, 55%, 40%)', 'hsl(45, 80%, 45%)', 'hsl(0, 60%, 50%)'] }],
        config: { legend: { position: 'right' }, tooltip: { label: (item) => `${item.value} submissions` } },
    },
    parameters: {
        layout: 'padded',
    },
    decorators: [
        (story) => ({
            ...story(),
            template: `<div style="height: 300px">${story().template ?? ''}</div>`,
        }),
    ],
} satisfies Meta<TumUiDoughnutChartComponent>;

export default meta;

type Story = StoryObj<TumUiDoughnutChartComponent>;

export const Default: Story = {};

export const Pie: Story = {
    args: {
        config: { arcWidth: 1, legend: { position: 'bottom' } },
    },
};

export const ThickRing: Story = {
    args: {
        config: { arcWidth: 0.5, legend: { position: 'right' } },
    },
};

export const SingleValue: Story = {
    args: {
        ariaLabel: 'Course completion',
        labels: ['Completed'],
        series: [{ data: [100], colors: ['var(--tumaet-ui-primary-color)'] }],
        config: { arcWidth: 0.3 },
    },
};
