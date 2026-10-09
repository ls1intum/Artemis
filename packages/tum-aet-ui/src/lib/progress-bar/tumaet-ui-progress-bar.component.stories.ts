import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiProgressBarComponent } from './tumaet-ui-progress-bar.component';

const meta = {
    title: 'Feedback/Progress Bar',
    component: TumAetUiProgressBarComponent,
    args: {
        ariaLabel: 'Course completion',
        severity: 'primary',
        size: 'default',
        value: 60,
        showValue: true,
        unit: '%',
    },
    argTypes: {
        severity: {
            control: 'select',
            options: ['primary', 'success', 'warn', 'danger', 'info'],
        },
        size: {
            control: 'inline-radio',
            options: ['small', 'default'],
        },
        value: {
            control: {
                type: 'range',
                min: 0,
                max: 100,
                step: 1,
            },
        },
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumAetUiProgressBarComponent>;

export default meta;

type Story = StoryObj<TumAetUiProgressBarComponent>;

export const Default: Story = {};

export const Empty: Story = {
    args: {
        value: 0,
    },
};

export const Complete: Story = {
    args: {
        value: 100,
    },
};

export const Small: Story = {
    args: {
        size: 'small',
        severity: 'success',
    },
};
