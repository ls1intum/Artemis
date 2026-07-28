import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiProgressSpinnerComponent } from './tum-ui-progress-spinner.component';

const meta = {
    title: 'Feedback/Progress Spinner',
    component: TumUiProgressSpinnerComponent,
    args: {
        ariaLabel: 'Loading course data',
        animationDuration: '2s',
        fill: 'none',
        strokeWidth: 2,
    },
} satisfies Meta<TumUiProgressSpinnerComponent>;

export default meta;

type Story = StoryObj<TumUiProgressSpinnerComponent>;

export const Default: Story = {};
