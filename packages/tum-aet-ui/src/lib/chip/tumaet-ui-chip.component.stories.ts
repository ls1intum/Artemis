import type { Meta, StoryObj } from '@storybook/angular-vite';
import { fn } from 'storybook/test';

import { TumAetUiChipComponent } from './tumaet-ui-chip.component';

const meta = {
    title: 'Data Display/Chip',
    component: TumAetUiChipComponent,
    args: {
        label: 'Machine Learning',
        removed: fn(),
        removable: false,
        removeAriaLabel: 'Remove Machine Learning',
    },
} satisfies Meta<TumAetUiChipComponent>;

export default meta;

type Story = StoryObj<TumAetUiChipComponent>;

export const Default: Story = {};

export const Removable: Story = {
    args: {
        removable: true,
    },
};

export const Compact: Story = {
    args: {
        label: 'Compact',
        size: 'small',
    },
};
