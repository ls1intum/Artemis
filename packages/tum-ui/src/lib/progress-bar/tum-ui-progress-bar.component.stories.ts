import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiProgressBarComponent } from './tum-ui-progress-bar.component';

const meta = {
    title: 'Feedback/Progress Bar',
    component: TumUiProgressBarComponent,
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
} satisfies Meta<TumUiProgressBarComponent>;

export default meta;

type Story = StoryObj<TumUiProgressBarComponent>;

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
