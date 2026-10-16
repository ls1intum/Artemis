import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiProgressSpinnerComponent } from './tumaet-ui-progress-spinner.component';

const meta = {
    title: 'Feedback/Progress Spinner',
    component: TumAetUiProgressSpinnerComponent,
    args: {
        ariaLabel: 'Loading course data',
    },
} satisfies Meta<TumAetUiProgressSpinnerComponent>;

export default meta;

type Story = StoryObj<TumAetUiProgressSpinnerComponent>;

export const Default: Story = {};
